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

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<FineResponseDTO>> listByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "dateGenerated") Pageable pageable) {
        return ResponseEntity.ok(
                fineService.listByUser(userId, authentication, pageable));
    }

    @GetMapping("/usuario/{usuarioId}/detalle")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<FineDetailResponseDTO>> listDetailByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "statusFineId") Pageable pageable) {
        return ResponseEntity.ok(
                fineService.listDetailByUser(userId, authentication, pageable));
    }

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
     * Voids Response Entity&lt;Multa Accion Response DTO>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Multa Accion Response DTO>
     * @param dto Anulacion fine Request data transfer object used to scope this Response Entity&lt;Multa Accion Response DTO>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Multa Accion Response DTO>
     * @return Response Entity&lt;Multa Accion Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<FineActionResponseDTO> annul(
            @PathVariable Long id,
            @Valid @RequestBody CancellationFineRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(fineService.annul(id, dto.reason(), authentication));
    }

    @GetMapping("/reportes/resumen-financiero")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<SummaryFinancialFinesResponseDTO> reportSummaryFinancial(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        return ResponseEntity.ok(fineService.reportSummaryFinancial(from, until));
    }

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
