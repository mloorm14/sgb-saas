package com.uteq.backend.controller;

import com.uteq.backend.dto.AnulacionMultaRequestDTO;
import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.service.MultaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/multas")
public class MultaController {

    private final MultaService multaService;

    public MultaController(MultaService multaService) {
        this.multaService = multaService;
    }

    // ── GET /api/v1/multas/usuario/{usuarioId} ────────────
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<Page<MultaResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "fechaGenerada") Pageable pageable) {
        return ResponseEntity.ok(
                multaService.listarPorUsuario(usuarioId, authentication, pageable));
    }

    // ── POST /api/v1/multas/{id}/pago ─────────────────────
    @PostMapping("/{id}/pago")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<MultaAccionResponseDTO> pagar(@PathVariable Long id) {
        return ResponseEntity.ok(multaService.pagar(id));
    }

    // ── POST /api/v1/multas/{id}/anulacion ────────────────
    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<MultaAccionResponseDTO> anular(
            @PathVariable Long id,
            @Valid @RequestBody AnulacionMultaRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(multaService.anular(id, dto.motivo(), authentication));
    }

    // ── GET /api/v1/multas/reportes/resumen-financiero?desde=&hasta= ──
    // GERENTE/ADMIN (no BIBLIOTECARIO): datos financieros agregados de toda
    // la biblioteca, mismo criterio de sensibilidad que anular() arriba y
    // que AuditoriaController. A diferencia de PrestamoController.reportes/*
    // (BIBLIOTECARIO/GERENTE, no ADMIN -- decisión previa y deliberada, ver
    // comentario en PrestamoController.reporteMorosidad), este reporte SÍ
    // incluye ADMIN a propósito, pedido explícito del dashboard
    // GERENTE/ADMIN de Cajas. No se amplió el roleGuard de
    // /dashboard-gerente en el frontend a ADMIN todavía porque esa pantalla
    // también depende de /reportes/libros-mas-prestados y
    // /reportes/morosidad, que siguen sin incluir ADMIN -- ampliar el guard
    // ahora dejaría 2 de 4 widgets rotos (403) para un usuario ADMIN real;
    // queda como seguimiento si se decide ampliar esos 2 endpoints también.
    @GetMapping("/reportes/resumen-financiero")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<ResumenFinancieroMultasResponseDTO> reporteResumenFinanciero(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(multaService.reporteResumenFinanciero(desde, hasta));
    }
}