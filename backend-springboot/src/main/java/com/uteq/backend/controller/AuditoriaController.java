package com.uteq.backend.controller;

import com.uteq.backend.dto.EventoAuditoriaResponseDTO;
import com.uteq.backend.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Módulo 6: consulta de bitácora de auditoría, solo GERENTE/ADMIN (mismo
 * criterio de acceso que {@code MultaController.anular} y los reportes
 * gerenciales -- ver también {@code UsuarioAdminController.listar}, que
 * comparte los dos mismos roles para lectura).
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // ── GET /api/v1/auditoria?usuarioId=&modulo=&desde=&hasta= ──
    @GetMapping
    public ResponseEntity<Page<EventoAuditoriaResponseDTO>> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @PageableDefault(size = 20, sort = "fechaHora") Pageable pageable) {
        return ResponseEntity.ok(
                auditoriaService.listar(usuarioId, modulo, desde, hasta, pageable));
    }
}
