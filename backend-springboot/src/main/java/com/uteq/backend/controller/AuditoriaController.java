package com.uteq.backend.controller;

import com.uteq.backend.dto.EventoAuditoriaResponseDTO;
import com.uteq.backend.dto.ResumenCategoriaAuditoriaDTO;
import com.uteq.backend.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

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
            // sort con el nombre FISICO de columna (fecha_hora): la query del
            // repositorio es NATIVA (BitacoraAuditoriaRepository) y Spring Data
            // inyecta el sort tal cual, sin traducir propiedad->columna.
            @PageableDefault(size = 20, sort = "fecha_hora", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                auditoriaService.listar(usuarioId, modulo, desde, hasta, pageable));
    }

    // ── GET /api/v1/auditoria/resumen ────────────────────────
    // Agregación por tabla_afectada: total, hoy, último evento.
    // Misma restricción @PreAuthorize que el listado (GERENTE/ADMIN).
    @GetMapping("/resumen")
    public ResponseEntity<List<ResumenCategoriaAuditoriaDTO>> resumen() {
        return ResponseEntity.ok(auditoriaService.resumen());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String formato,
                                         @RequestParam(required = false) Long usuarioId,
                                         @RequestParam(required = false) String modulo,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        byte[] data = auditoriaService.exportarCsv(usuarioId, modulo, desde, hasta);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria.csv").contentType(MediaType.parseMediaType("text/csv")).contentLength(data.length).body(data);
    }
}
