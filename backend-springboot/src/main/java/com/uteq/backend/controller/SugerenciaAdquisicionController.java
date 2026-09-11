package com.uteq.backend.controller;

import com.uteq.backend.dto.CambioEstadoSugerenciaRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
import com.uteq.backend.dto.SugerenciaAgrupadaDTO;
import com.uteq.backend.service.ReportePdfService;
import com.uteq.backend.service.SugerenciaAdquisicionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Lector crea, gerente lista y cambia estado. LECTOR ve solo las
// suyas; GERENTE/ADMIN ven todas.
@RestController
@RequestMapping("/api/v1/sugerencias-adquisicion")
public class SugerenciaAdquisicionController {

    private final SugerenciaAdquisicionService sugerenciaService;
    private final ReportePdfService reportePdfService;

    public SugerenciaAdquisicionController(SugerenciaAdquisicionService sugerenciaService,
                                           ReportePdfService reportePdfService) {
        this.sugerenciaService = sugerenciaService;
        this.reportePdfService = reportePdfService;
    }

    // ── POST /api/v1/sugerencias-adquisicion ──────────────
    @PostMapping
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Executes the crear operation.
     * @param dto value required by the operation
     * @param authentication value required by the operation
     * @return operation result
     */
    public ResponseEntity<SugerenciaAdquisicionResponseDTO> crear(
            @Valid @RequestBody SugerenciaAdquisicionRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sugerenciaService.crear(dto, authentication));
    }

    // ── GET /api/v1/sugerencias-adquisicion/mias ──────────
    @GetMapping("/mias")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Page<SugerenciaAdquisicionResponseDTO>> listarPropias(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "creadoEn") Pageable pageable) {
        return ResponseEntity.ok(sugerenciaService.listarPropias(authentication, pageable));
    }

    // ── GET /api/v1/sugerencias-adquisicion?estado=PENDIENTE ──
    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SugerenciaAdquisicionResponseDTO>> listarTodas(
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 10, sort = "creadoEn") Pageable pageable) {
        return ResponseEntity.ok(sugerenciaService.listarTodas(estado, pageable));
    }

    // ── PATCH /api/v1/sugerencias-adquisicion/{id}/estado ─
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Executes the cambiarEstado operation.
     * @param id value required by the operation
     * @param dto value required by the operation
     * @param authentication value required by the operation
     * @return operation result
     */
    public ResponseEntity<SugerenciaAdquisicionResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoSugerenciaRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(
                sugerenciaService.cambiarEstado(id, dto.nuevoEstado(), authentication));
    }

    // ── GET /api/v1/sugerencias-adquisicion/mas-pedidos ──
    // Gestión por demanda (GERENTE/ADMIN): PENDIENTE agrupadas por ISBN.
    // Importante: va ANTES de que alguien agregue un @GetMapping("/{id}")
    // para que "mas-pedidos" no se confunda con un id.
    @GetMapping("/mas-pedidos")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SugerenciaAgrupadaDTO>> masPedidos(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(sugerenciaService.getMasPedidos(pageable));
    }

    // ── POST /api/v1/sugerencias-adquisicion/confirmar-adquisicion?isbn= ──
    // Marca adquiridas todas las PENDIENTE de ese ISBN (salen del agrupado).
    @PostMapping("/confirmar-adquisicion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> confirmarAdquisicion(
            @RequestParam String isbn,
            Authentication authentication) {
        Long revisorId = authentication == null ? null
                : sugerenciaService.resolverIdPorCorreoPublico(authentication.getName());
        int confirmadas = sugerenciaService.confirmarAdquisicion(isbn, revisorId);
        return ResponseEntity.ok(java.util.Map.of("isbn", isbn, "confirmadas", confirmadas));
    }

    // ── GET /api/v1/sugerencias-adquisicion/reporte-pdf ──
    @GetMapping("/reporte-pdf")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Executes the reportePdf operation.
     * @return operation result
     */
    public ResponseEntity<byte[]> reportePdf() {
        byte[] pdf = reportePdfService.generarReporteSugerenciasMasPedidas(
                sugerenciaService.getMasPedidosList());
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reporte-sugerencias-mas-pedidas.pdf")
                .body(pdf);
    }
}
