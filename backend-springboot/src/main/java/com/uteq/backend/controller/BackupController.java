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
    public ResponseEntity<List<BackupResumenDTO>> listar(@RequestParam(required = false) OffsetDateTime desde, @RequestParam(required = false) OffsetDateTime hasta) {
        List<Backup> lista = (desde != null || hasta != null) ? backupService.listarPorRango(desde != null ? desde : OffsetDateTime.now().minusDays(30), hasta != null ? hasta : OffsetDateTime.now()) : backupService.listarTodos();
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
        @NotNull OffsetDateTime desde;
        @NotNull OffsetDateTime hasta;
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
}
