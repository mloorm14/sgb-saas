package com.uteq.backend.controller;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.service.BackupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/backups")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    // ── POST /api/v1/admin/backups ──────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackupResponseDTO> generar(@Valid BackupRequestDTO req) {
        Backup backup = backupService.generarBackup(req);

        String urlDescarga = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}/download")
                .buildAndExpand(backup.getId())
                .toUriString();

        BackupResponseDTO response = new BackupResponseDTO(
                backup.getId(),
                backup.getCreadoEn(),
                backup.getDesde(),
                backup.getHasta(),
                backup.getTablas(),
                backup.getFormato(),
                backup.getRuta(),
                backup.getTamanoBytes(),
                backup.getEstado(),
                urlDescarga
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/v1/admin/backups ───────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BackupResumenDTO>> listar(
            @RequestParam(required = false) OffsetDateTime desde,
            @RequestParam(required = false) OffsetDateTime hasta,
            @RequestParam(required = false) String estado) {

        List<Backup> lista;
        if (desde != null || hasta != null) {
            OffsetDateTime desdeDef = desde != null ? desde : OffsetDateTime.now().minusDays(30);
            OffsetDateTime hastaDef = hasta != null ? hasta : OffsetDateTime.now();
            lista = backupService.listarPorRango(desdeDef, hastaDef);
        } else {
            lista = backupService.listarTodos();
        }

        List<BackupResumenDTO> response = lista.stream()
                .map(b -> new BackupResumenDTO(
                        b.getId(),
                        b.getCreadoEn(),
                        b.getDesde(),
                        b.getHasta(),
                        b.getTablas(),
                        b.getFormato(),
                        b.getTamanoBytes(),
                        b.getEstado()))
                .toList();

        return ResponseEntity.ok(response);
    }

    // ── GET /api/v1/admin/backups/{id}/download ────────────────
    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) throws IOException {
        Backup backup = backupService.obtenerPorId(id);
        File archivo = new File(backup.getRuta());
        if (!archivo.exists()) {
            throw new RuntimeException("Archivo de backup no encontrado: " + backup.getRuta());
        }
        byte[] fileBytes = Files.readAllBytes(archivo.toPath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + "backup_" + id + ".zip\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes);
    }

    // ── DELETE /api/v1/admin/backups/{id} ───────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> borrar(@PathVariable Long id) {
        backupService.obtenerPorId(id);
        // Borrar archivo del storage (local o S3)
        // File f = new File(backup.getRuta());
        // f.delete();
        // backupRepository.delete(backup);
        return ResponseEntity.noContent().build();
    }

    // --- DTOs de apoyo ---

    public static class BackupRequestDTO {
        @NotNull(message = "El parámetro 'desde' es obligatorio")
        OffsetDateTime desde;

        @NotNull(message = "El parámetro 'hasta' es obligatorio")
        @Past(message = "La fecha 'hasta' no puede ser futura")
        @FutureOrPresent(message = "La fecha 'hasta' puede ser ahora o pasada")
        OffsetDateTime hasta;

        @NotEmpty(message = "Debe seleccionar al menos una tabla")
        @Size(min = 1, message = "Debe seleccionar al menos una tabla")
        Set<String> tablas;

        @NotNull(message = "El formato es obligatorio")
        @Size(min = 1, max = 3, message = "Formato debe ser 'sql' o 'csv'")
        String formato;
    }

    public static class BackupResponseDTO {
        Long id;
        OffsetDateTime creadoEn;
        OffsetDateTime desde;
        OffsetDateTime hasta;
        Set<String> tablas;
        String formato;
        String ruta;
        Long tamanoBytes;
        String estado;
        String urlDescarga;
    }

    public static class BackupResumenDTO {
        Long id;
        OffsetDateTime creadoEn;
        OffsetDateTime desde;
        OffsetDateTime hasta;
        Set<String> tablas;
        String formato;
        Long tamanoBytes;
        String estado;
    }
}