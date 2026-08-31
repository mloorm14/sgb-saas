package com.uteq.backend.controller;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.service.RespaldoCompletoService;
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

    private final RespaldoCompletoService service;

    public RespaldoCompletoController(RespaldoCompletoService service) {
        this.service = service;
    }

    // ── Configuración DR ──────────────────────────────────────────────────────
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

    // ── Registro de ejecución (llamado desde el microservicio Node.js vía token interno) ──
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
