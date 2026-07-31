package com.uteq.backend.controller;

import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.service.ReservacionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class ReservacionController {

    private final ReservacionService reservacionService;

    public ReservacionController(ReservacionService reservacionService) {
        this.reservacionService = reservacionService;
    }

    // ── POST /api/v1/reservaciones ────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<ReservacionResponseDTO> crear(
            @Valid @RequestBody ReservacionRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservacionService.crear(dto, authentication));
    }

    // ── GET /api/v1/reservaciones/usuario/{usuarioId} ─────
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<Page<ReservacionResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "fechaReserva") Pageable pageable) {
        return ResponseEntity.ok(
                reservacionService.listarPorUsuario(usuarioId, authentication, pageable));
    }
}