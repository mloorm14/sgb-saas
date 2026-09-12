package com.uteq.backend.controller;

import com.uteq.backend.dto.EventAuditResponseDTO;
import com.uteq.backend.dto.SummaryCategoryAuditDTO;
import com.uteq.backend.service.AuditService;
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
 * Consulta de bitácora de auditoría, solo GERENTE/ADMIN.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // ── GET /api/v1/auditoria?usuarioId=&modulo=&desde=&hasta= ──
    /**
     * Consulta list usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param module criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param cual valor de entrada cual usado por la operacion para completar su regla de negocio
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping
    public ResponseEntity<Page<EventAuditResponseDTO>> list(
            @RequestParam(name = "usuarioId", required = false) Long userId,
            @RequestParam(name = "modulo", required = false) String module,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            // sort con el nombre FISICO de columna (fecha_hora): la query del
            // repositorio es NATIVA (BitacoraAuditoriaRepository) y Spring Data
            // inyecta el sort tal cual, sin traducir propiedad->columna.
            @PageableDefault(size = 20, sort = "date_time", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                auditService.list(userId, module, from, until, pageable));
    }

    // ── GET /api/v1/auditoria/resumen ────────────────────────
    // Agregación por tabla_afectada: total, hoy, último evento.
    // Misma restricción @PreAuthorize que el listado (GERENTE/ADMIN).
    @GetMapping("/resumen")
    /**
     * Procesa summary y devuelve el resultado calculado por el backend.
     *
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<List<SummaryCategoryAuditDTO>> summary() {
        return ResponseEntity.ok(auditService.summary());
    }
    /**
     * Genera o entrega export a partir de los datos actuales del sistema.
     *
     * @param format criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param module criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "csv") String format,
                                         @RequestParam(name = "usuarioId", required = false) Long userId,
                                         @RequestParam(name = "modulo", required = false) String module,
                                         @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                         @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        byte[] data = auditService.exportarCsv(userId, module, from, until);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria.csv").contentType(MediaType.parseMediaType("text/csv")).contentLength(data.length).body(data);
    }
}
