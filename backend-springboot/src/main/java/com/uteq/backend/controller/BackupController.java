package com.uteq.backend.controller;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupProgramacion;
import com.uteq.backend.service.BackupProgramacionService;
import com.uteq.backend.service.BackupService;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.uteq.backend.config.FlexibleOffsetDateTimeDeserializer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/backups")
public class BackupController {

    private final BackupService backupService;
    private final BackupProgramacionService progService;

    public BackupController(BackupService backupService, BackupProgramacionService progService) {
        this.backupService = backupService;
        this.progService = progService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupResponseDTO> generar(@RequestBody BackupRequestDTO req) {
        String tipo = req.tipo != null ? req.tipo : "manual";
        Backup b = backupService.generarBackup(req.desde, req.hasta, req.tablas, req.formato, tipo);
        String url = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}/download").buildAndExpand(b.getId()).toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(new BackupResponseDTO(b.getId(), b.getCreadoEn(), b.getDesde(), b.getHasta(), b.getTablas(), b.getFormato(), b.getRuta(), b.getTamanoBytes(), b.getEstado(), b.getTipo(), url));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BackupResumenDTO>> listar(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        OffsetDateTime d = desde != null ? parseFlexible(desde) : null;
        OffsetDateTime h = hasta != null ? parseFlexible(hasta) : null;
        List<Backup> lista = (d != null || h != null) ? backupService.listarPorRango(d != null ? d : OffsetDateTime.now().minusDays(30), h != null ? h : OffsetDateTime.now()) : backupService.listarTodos();
        return ResponseEntity.ok(lista.stream().map(b -> new BackupResumenDTO(b.getId(), b.getCreadoEn(), b.getDesde(), b.getHasta(), b.getTablas(), b.getFormato(), b.getTamanoBytes(), b.getEstado(), b.getTipo())).toList());
    }

    @GetMapping("/programacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BackupProgramacion>> listarProgramaciones() {
        return ResponseEntity.ok(progService.listarActivas());
    }

    @PostMapping("/programacion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupProgramacion> crearProgramacion(@RequestBody BackupProgramacion req) {
        BackupProgramacion creada = progService.crear(req);
        // Opcional: auto-programar al crear
        if (Boolean.TRUE.equals(creada.getActivo())) {
            progService.programarEjecucion(creada.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PostMapping("/{id}/programar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> programar(@PathVariable Long id) {
        progService.programarEjecucion(id);
        return ResponseEntity.ok().body("Programación activa para backup id=" + id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> borrar(@PathVariable Long id) {
        backupService.eliminar(id);
        progService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ejecutar-ahora")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> ejecutarAhora(@PathVariable Long id) {
        BackupProgramacion p = progService.obtener(id);
        // Ejecutar backup inmediato con el rango correspondiente
        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime desde, hasta;
        if (p.getCadaHoras() != null) {
            desde = ahora.minusHours(p.getCadaHoras());
            hasta = ahora;
        } else {
            desde = ahora.minusDays(p.getCadaDias()).withHour(0).withMinute(0).withSecond(0).withNano(0);
            hasta = ahora.withHour(23).withMinute(59).withSecond(59);
        }
        Set<String> tablas = (p.getTablas() != null && !p.getTablas().isEmpty()) ? p.getTablas() : Set.of();
        Backup backup = backupService.generarBackup(desde, hasta, tablas, p.getFormato(), "automatico");
        // Actualizar última ejecución en la programación (usa método interno del service)
        progService.actualizarUltimaEjecucion(id, backup.getCreadoEn());
        return ResponseEntity.ok().body("Backup ejecutado id=" + backup.getId());
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupRequestDTO {
        @NotNull @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime desde;
        @NotNull @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime hasta;
        @NotEmpty Set<String> tablas;
        @NotNull String formato;
        String tipo; // "manual" o "automatico", default "manual"
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupResponseDTO {
        Long id; OffsetDateTime creadoEn; OffsetDateTime desde; OffsetDateTime hasta; Set<String> tablas; String formato; String ruta; Long tamanoBytes; String estado; String tipo; String urlDescarga;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupResumenDTO {
        Long id; OffsetDateTime creadoEn; OffsetDateTime desde; OffsetDateTime hasta; Set<String> tablas; String formato; Long tamanoBytes; String estado; String tipo;
    }

    private static OffsetDateTime parseFlexible(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.trim().replace(' ', 'T');
        try { return OffsetDateTime.parse(text); } catch (Exception ignored) {}
        try {
            java.time.format.DateTimeFormatter fmt = new java.time.format.DateTimeFormatterBuilder()
                    .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral('T').appendPattern("HH:mm")
                    .optionalStart().appendPattern(":ss").optionalEnd()
                    .parseDefaulting(java.time.temporal.ChronoField.SECOND_OF_MINUTE, 0)
                    .parseDefaulting(java.time.temporal.ChronoField.OFFSET_SECONDS, -5*3600).toFormatter();
            return OffsetDateTime.parse(text, fmt);
        } catch (Exception e) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Formato de fecha inválido: " + text); }
    }
}