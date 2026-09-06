package com.uteq.backend.controller;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.service.RespaldoCompletoEjecutor;
import com.uteq.backend.service.RespaldoCompletoService;
import com.uteq.backend.service.RespaldoEnCursoException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/respaldo-completo")
public class RespaldoCompletoController {

    private static final String CLAVE_MENSAJE = "mensaje";
    private static final String CLAVE_DETALLE = "detalle";

    private final RespaldoCompletoService service;
    private final RespaldoCompletoEjecutor ejecutor;

    public RespaldoCompletoController(RespaldoCompletoService service, RespaldoCompletoEjecutor ejecutor) {
        this.service = service;
        this.ejecutor = ejecutor;
    }

    // ── Configuración ────────────────────────────────────────────────────────
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfiguracionRespaldo> obtenerConfig() {
        return ResponseEntity.ok(service.obtenerConfiguracion());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfiguracionRespaldo> actualizarConfig(@RequestBody ConfigRequestDTO req) {
        return ResponseEntity.ok(service.actualizarConfiguracion(req.frecuenciaHoras, req.diasRetencion, req.habilitado));
    }

    // ── Historial de registros ─────────────────────────────────────────────────
    @GetMapping("/registros")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RegistroRespaldo>> listarRegistros(
            @RequestParam(required = false) String tipo) {
        List<RegistroRespaldo> lista = (tipo != null && !tipo.isBlank())
                ? service.listarPorTipo(tipo)
                : service.listarTodos();
        return ResponseEntity.ok(lista);
    }

    @DeleteMapping("/registros/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminarRegistro(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/registros/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> descargarRegistro(@PathVariable Long id) {
        byte[] contenido = service.descargar(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=backup-completo-" + id + ".zip")
                .contentLength(contenido.length)
                .body(contenido);
    }

    // ── Registro de ejecución ──────────────────────────────────────────────────
    @PostMapping("/registros")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistroRespaldo> registrarInicio(@RequestBody RegistroInicioDTO req) {
        return ResponseEntity.ok(service.registrarInicio(req.tipo, req.ejecutadoPor));
    }

    @PutMapping("/registros/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistroRespaldo> registrarResultado(
            @PathVariable Long id, @RequestBody RegistroResultadoDTO req) {
        return ResponseEntity.ok(service.registrarResultado(
                id, req.estado, req.nombreArchivo, req.tamanoArchivoBytes, req.rutaR2, req.mensajeError));
    }

    // ── Disparo manual: ejecución en background dentro del backend ─────────────
    // (antes proxy al microservicio Node.js; fusionado: sin salto HTTP, el 429
    // solo sale del cerrojo real del ejecutor).
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> triggerBackupCompleto() {
        try {
            ejecutor.dispararManual();
        } catch (RespaldoEnCursoException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .body(java.util.Map.of(CLAVE_MENSAJE, "Ya hay un respaldo en ejecucion", CLAVE_DETALLE, ""));
        }
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                .body(java.util.Map.of(CLAVE_MENSAJE, "Backup completo iniciado", CLAVE_DETALLE, ""));
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfigRequestDTO {
        Integer frecuenciaHoras;
        Integer diasRetencion;
        Boolean habilitado;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegistroInicioDTO {
        String tipo;
        Long ejecutadoPor;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegistroResultadoDTO {
        String estado;
        String nombreArchivo;
        Long tamanoArchivoBytes;
        String rutaR2;
        String mensajeError;
    }
}
