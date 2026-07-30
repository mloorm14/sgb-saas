package com.uteq.backend.controller;

import com.uteq.backend.dto.AnulacionMultaRequestDTO;
import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.service.MultaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}