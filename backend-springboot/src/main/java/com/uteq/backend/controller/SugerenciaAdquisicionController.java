package com.uteq.backend.controller;

import com.uteq.backend.dto.CambioEstadoSugerenciaRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
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

// Módulo 9.3 del roadmap. "lector crea, gerente lista y cambia estado":
// GERENTE/ADMIN pueden ver TODAS las sugerencias (listarTodas), LECTOR
// solo las suyas (listarPropias) -- mismo patrón de separación que
// PrestamoController.listarPorUsuario vs listarActivosPorUsuario, pero acá
// son dos endpoints distintos en vez de uno con chequeo de acceso interno,
// porque el filtro (propias vs todas) cambia según el rol, no según un
// parámetro que el cliente elige.
@RestController
@RequestMapping("/api/v1/sugerencias-adquisicion")
public class SugerenciaAdquisicionController {

    private final SugerenciaAdquisicionService sugerenciaService;

    public SugerenciaAdquisicionController(SugerenciaAdquisicionService sugerenciaService) {
        this.sugerenciaService = sugerenciaService;
    }

    // ── POST /api/v1/sugerencias-adquisicion ──────────────
    @PostMapping
    @PreAuthorize("hasRole('LECTOR')")
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
    public ResponseEntity<SugerenciaAdquisicionResponseDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoSugerenciaRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(
                sugerenciaService.cambiarEstado(id, dto.nuevoEstado(), authentication));
    }
}
