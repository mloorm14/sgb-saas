package com.uteq.backend.controller;

import com.uteq.backend.dto.NotificacionResponseDTO;
import com.uteq.backend.service.NotificacionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    // ── GET /api/v1/notificaciones/usuario/{usuarioId} ────
    // Mismo patrón de autorización que MultaController: un LECTOR solo ve
    // las suyas (validado en NotificacionService), el resto de roles puede
    // consultar cualquiera.
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<NotificacionResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "creadoEn") Pageable pageable) {
        return ResponseEntity.ok(notificacionService.listarPorUsuario(usuarioId, authentication, pageable));
    }
}
