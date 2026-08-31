package com.uteq.backend.controller;

import com.uteq.backend.entity.Backup;
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
    public BackupController(BackupService backupService) { this.backupService = backupService; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupResponseDTO> generar(@RequestBody BackupRequestDTO req) {
        Backup b = backupService.generarBackup(req.desde, req.hasta, req.tablas, req.formato);
        String url = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}/download").buildAndExpand(b.getId()).toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(new BackupResponseDTO(b.getId(), b.getCreadoEn(), b.getDesde(), b.getHasta(), b.getTablas(), b.getFormato(), b.getRuta(), b.getTamanoBytes(), b.getEstado(), url));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BackupResumenDTO>> listar(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        OffsetDateTime d = desde != null ? parseFlexible(desde) : null;
        OffsetDateTime h = hasta != null ? parseFlexible(hasta) : null;
        List<Backup> lista = (d != null || h != null) ? backupService.listarPorRango(d != null ? d : OffsetDateTime.now().minusDays(30), h != null ? h : OffsetDateTime.now()) : backupService.listarTodos();
        return ResponseEntity.ok(lista.stream().map(b -> new BackupResumenDTO(b.getId(), b.getCreadoEn(), b.getDesde(), b.getHasta(), b.getTablas(), b.getFormato(), b.getTamanoBytes(), b.getEstado())).toList());
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        byte[] data = backupService.descargar(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"backup_" + id + ".zip\"").contentType(MediaType.APPLICATION_OCTET_STREAM).body(data);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> borrar(@PathVariable Long id) {
        backupService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupRequestDTO {
        @NotNull @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime desde;
        @NotNull @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class) OffsetDateTime hasta;
        @NotEmpty Set<String> tablas;
        @NotNull String formato;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupResponseDTO {
        Long id; OffsetDateTime creadoEn; OffsetDateTime desde; OffsetDateTime hasta; Set<String> tablas; String formato; String ruta; Long tamanoBytes; String estado; String urlDescarga;
    }
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class BackupResumenDTO {
        Long id; OffsetDateTime creadoEn; OffsetDateTime desde; OffsetDateTime hasta; Set<String> tablas; String formato; Long tamanoBytes; String estado;
    }

    private static OffsetDateTime parseFlexible(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.trim().replace(' ', 'T');
        // intenta OffsetDateTime directo, si falla usa deserializer
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
