package com.uteq.backend.controller;

import com.uteq.backend.dto.AnulacionMultaRequestDTO;
import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaDetalleResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.PagoMultaRequestDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.service.MultaService;
import com.uteq.backend.service.NotificacionService;
import com.uteq.backend.service.ReportePdfService;
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
public class MultaController {

    private final MultaService multaService;
    private final NotificacionService notificacionService;
    private final ReportePdfService reportePdfService;

    public MultaController(MultaService multaService, NotificacionService notificacionService, ReportePdfService reportePdfService) {
        this.multaService = multaService;
        this.notificacionService = notificacionService;
        this.reportePdfService = reportePdfService;
    }

    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<MultaResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "fechaGenerada") Pageable pageable) {
        return ResponseEntity.ok(
                multaService.listarPorUsuario(usuarioId, authentication, pageable));
    }

    @GetMapping("/usuario/{usuarioId}/detalle")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<MultaDetalleResponseDTO>> listarDetallePorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "estadoMultaId") Pageable pageable) {
        return ResponseEntity.ok(
                multaService.listarDetallePorUsuario(usuarioId, authentication, pageable));
    }

    @PostMapping("/{id}/pago")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Map<String, Object>> pagar(
            @PathVariable Long id,
            @RequestBody(required = false) PagoMultaRequestDTO body) {
        BigDecimal montoPagado = body != null ? body.montoPagado() : null;

        Map<String, Object> resultado;
        if (montoPagado != null) {
            resultado = multaService.pagoParcial(id, montoPagado);
        } else {
            var accion = multaService.pagar(id);
            resultado = Map.of(
                    "o_multa_id", accion.multaId(),
                    "o_usuario_desbloqueado", accion.usuarioDesbloqueado(),
                    "o_estado", "PAGADA",
                    "o_saldo_restante", BigDecimal.ZERO);
        }

        Long usuarioId = multaService.resolverUsuarioIdDeMulta(id);
        notificacionService.notificarComprobantePago(usuarioId, id, montoPagado);

        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<MultaAccionResponseDTO> anular(
            @PathVariable Long id,
            @Valid @RequestBody AnulacionMultaRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(multaService.anular(id, dto.motivo(), authentication));
    }

    @GetMapping("/reportes/resumen-financiero")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<ResumenFinancieroMultasResponseDTO> reporteResumenFinanciero(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(multaService.reporteResumenFinanciero(desde, hasta));
    }

    @GetMapping(value = "/reportes/resumen-financiero/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteResumenFinancieroPdf(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        ResumenFinancieroMultasResponseDTO dto = multaService.reporteResumenFinanciero(desde, hasta);
        byte[] pdf = reportePdfService.generarReporteResumenFinanciero(dto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-resumen-financiero.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
