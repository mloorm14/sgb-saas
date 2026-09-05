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

    private static final String CLAVE_MENSAJE = "mensaje";
    private static final String CLAVE_DETALLE = "detalle";

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

    // ── Proxy hacia el microservicio Node.js ───────────────────────────────────
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> triggerBackupCompleto(java.security.Principal principal) {
        String backupServiceUrl = System.getenv("BACKUP_SERVICE_URL");
        if (backupServiceUrl == null || backupServiceUrl.isBlank()) {
            backupServiceUrl = "http://localhost:3000";
        }

        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(90000); // 90 segundos para tolerar el cold-start de Render
            org.springframework.web.client.RestTemplate restTemplate =
                    new org.springframework.web.client.RestTemplate(factory);
            java.util.Map<String, Object> reqBody = new java.util.HashMap<>();
            if (principal != null) {
                // The frontend doesn't send the user ID in the proxy request, so we need to
                // pass a dummy ID or find it if we injected the user repo. But since we are proxying,
                // and the Node service can handle null usuarioId if not found, we can just send null
                // or try to fetch it if we had the repo. To keep it simple and compile-safe:
                reqBody.put("usuarioId", null);
            }
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            String internalApiKey = System.getenv("INTERNAL_API_KEY");
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                headers.set("x-internal-api-key", internalApiKey);
            }
            
            org.springframework.http.HttpEntity<java.util.Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(reqBody, headers);
            
            org.springframework.http.ResponseEntity<String> nodeResponse = restTemplate.postForEntity(
                backupServiceUrl + "/api/v1/trigger",
                requestEntity,
                String.class
            );
            if (nodeResponse.getStatusCode().value() == 429) {
                return ResponseEntity.status(429).body(java.util.Map.of(CLAVE_MENSAJE, "Ya hay un respaldo en ejecucion", CLAVE_DETALLE, nodeResponse.getBody() == null ? "" : nodeResponse.getBody()));
            }
            return ResponseEntity.status(nodeResponse.getStatusCode())
                    .body(java.util.Map.of(CLAVE_MENSAJE, "Backup completo iniciado",
                            CLAVE_DETALLE, nodeResponse.getBody() == null ? "" : nodeResponse.getBody()));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            int sc = e.getStatusCode().value();
            if (sc == 429) return ResponseEntity.status(429).body(java.util.Map.of(CLAVE_MENSAJE, "Ya hay un respaldo en ejecucion", CLAVE_DETALLE, e.getResponseBodyAsString()));
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of(CLAVE_MENSAJE, "Microservicio de respaldos no disponible", CLAVE_DETALLE, e.getResponseBodyAsString()));
        } catch (Exception e) {
            // Devolver JSON 503 en vez de texto HTML para que el frontend no rompa el parse.
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of(CLAVE_MENSAJE, "Microservicio de respaldos no disponible",
                            CLAVE_DETALLE, e.getMessage() == null ? "" : e.getMessage()));
        }
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
