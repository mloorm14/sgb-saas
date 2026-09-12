package com.uteq.backend.controller;

import com.uteq.backend.dto.CancellationFineRequestDTO;
import com.uteq.backend.dto.FineActionResponseDTO;
import com.uteq.backend.dto.FineDetailResponseDTO;
import com.uteq.backend.dto.FineResponseDTO;
import com.uteq.backend.dto.PaymentFineRequestDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import com.uteq.backend.service.FineService;
import com.uteq.backend.service.NotificationService;
import com.uteq.backend.service.ReportPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/multas")
public class FineController {

    private final FineService fineService;
    private final NotificationService notificationService;
    private final ReportPdfService reportPdfService;

    public FineController(FineService fineService, NotificationService notificationService, ReportPdfService reportPdfService) {
        this.fineService = fineService;
        this.notificationService = notificationService;
        this.reportPdfService = reportPdfService;
    }
    /**
     * Consulta list by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<FineResponseDTO>> listByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "dateGenerated") Pageable pageable) {
        return ResponseEntity.ok(
                fineService.listByUser(userId, authentication, pageable));
    }
    /**
     * Consulta list detail by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/usuario/{usuarioId}/detalle")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<FineDetailResponseDTO>> listDetailByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "statusFineId") Pageable pageable) {
        return ResponseEntity.ok(
                fineService.listDetailByUser(userId, authentication, pageable));
    }
    /**
     * Procesa pay y devuelve el resultado calculado por el backend.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param body datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */

    @PostMapping("/{id}/pago")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable Long id,
            @RequestBody(required = false) PaymentFineRequestDTO body) {
        BigDecimal amountPaid = body != null ? body.amountPaid() : null;

        Map<String, Object> result;
        if (amountPaid != null) {
            result = fineService.paymentParcial(id, amountPaid);
        } else {
            var action = fineService.pay(id);
            result = Map.of(
                    "o_multa_id", action.fineId(),
                    "o_usuario_desbloqueado", action.userUnblocked(),
                    "o_estado", "PAGADA",
                    "o_saldo_restante", BigDecimal.ZERO);
        }

        Long userId = fineService.resolveUserIdFine(id);
        notificationService.notifyReceiptPayment(userId, id, amountPaid);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Elimina o anula annul despues de validar que la operacion sea permitida.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<FineActionResponseDTO> annul(
            @PathVariable Long id,
            @Valid @RequestBody CancellationFineRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(fineService.annul(id, dto.reason(), authentication));
    }
    /**
     * Procesa report summary financial y devuelve el resultado calculado por el backend.
     *
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/resumen-financiero")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<SummaryFinancialFinesResponseDTO> reportSummaryFinancial(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        return ResponseEntity.ok(fineService.reportSummaryFinancial(from, until));
    }
    /**
     * Procesa report summary financial pdf y devuelve el resultado calculado por el backend.
     *
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping(value = "/reportes/resumen-financiero/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportSummaryFinancialPdf(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        SummaryFinancialFinesResponseDTO dto = fineService.reportSummaryFinancial(from, until);
        byte[] pdf = reportPdfService.generateReportSummaryFinancial(dto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-resumen-financiero.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
