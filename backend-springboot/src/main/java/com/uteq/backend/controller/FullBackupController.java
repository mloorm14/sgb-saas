package com.uteq.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uteq.backend.entity.ConfigurationBackup;
import com.uteq.backend.entity.RegistrationBackup;
import com.uteq.backend.service.FullBackupService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/respaldo-completo")
public class FullBackupController {

    private static final String CLAVE_MENSAJE = "mensaje";
    private static final String CLAVE_DETALLE = "detalle";

    private final FullBackupService service;

    public FullBackupController(FullBackupService service) {
        this.service = service;
    }

    // ── Configuración DR ──────────────────────────────────────────────────────
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Retrieves fig.
     *
     * @return response entity<configuracion respaldo> with the resulting state after the operation
     */
    public ResponseEntity<ConfigurationBackup> getConfig() {
        return ResponseEntity.ok(service.getConfiguration());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Updates Response Entity&lt;Configuracion Respaldo>.
     *
     * @param req Config Request data transfer object used to scope this Response Entity&lt;Configuracion Respaldo>
     * @return Response Entity&lt;Configuracion Respaldo> reflecting the state after the operation
     */
    public ResponseEntity<ConfigurationBackup> updateConfig(@RequestBody ConfigRequestDTO req) {
        return ResponseEntity.ok(service.updateConfiguration(req.frequencyTimes, req.daysRetention, req.enabled));
    }

    // ── Historial de registros ─────────────────────────────────────────────────
    @GetMapping("/registros")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RegistrationBackup>> listRegistrations(
            @RequestParam(name = "tipo", required = false) String type) {
        List<RegistrationBackup> lista = (type != null && !type.isBlank())
                ? service.listByType(type)
                : service.listAll();
        return ResponseEntity.ok(lista);
    }

    @DeleteMapping("/registros/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Deletes Response Entity&lt;Void>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> deleteRegistration(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/registros/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Downloads Response Entity&lt;byte[]>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;byte[]>
     * @return Response Entity&lt;byte[]> reflecting the state after the operation
     */
    public ResponseEntity<byte[]> downloadRegistration(@PathVariable Long id) {
        byte[] content = service.download(id);
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=backup-completo-" + id + ".zip")
                .contentLength(content.length)
                .body(content);
    }

    // ── Registro de ejecución (llamado desde el microservicio Node.js vía token interno) ──
    @PostMapping("/registros")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Registers Response Entity&lt;Registro Respaldo>.
     *
     * @param req record Inicio data transfer object used to scope this Response Entity&lt;Registro Respaldo>
     * @return Response Entity&lt;Registro Respaldo> reflecting the state after the operation
     */
    public ResponseEntity<RegistrationBackup> registerStart(@RequestBody RegistrationStartDTO req) {
        return ResponseEntity.ok(service.registerStart(req.type, req.executedBy));
    }

    @PutMapping("/registros/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Registers Response Entity&lt;Registro Respaldo>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Registro Respaldo>
     * @param req record Resultado data transfer object used to scope this Response Entity&lt;Registro Respaldo>
     * @return Response Entity&lt;Registro Respaldo> reflecting the state after the operation
     */
    public ResponseEntity<RegistrationBackup> registerResult(
            @PathVariable Long id, @RequestBody RegistrationResultDTO req) {
        return ResponseEntity.ok(service.registerResult(
                id, req.status, req.nameFile, req.sizeFileBytes, req.pathR2, req.messageError));
    }

    // ── Proxy hacia el microservicio Node.js ───────────────────────────────────
    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> triggerBackupFull(java.security.Principal principal) {
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
        @JsonProperty("frecuenciaHoras")
        Integer frequencyTimes;
        @JsonProperty("diasRetencion")
        Integer daysRetention;
        @JsonProperty("habilitado")
        Boolean enabled;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegistrationStartDTO {
        @JsonProperty("tipo")
        String type;
        @JsonProperty("ejecutadoPor")
        Long executedBy;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegistrationResultDTO {
        @JsonProperty("estado")
        String status;
        @JsonProperty("nombreArchivo")
        String nameFile;
        @JsonProperty("tamanoArchivoBytes")
        Long sizeFileBytes;
        @JsonProperty("rutaR2")
        String pathR2;
        @JsonProperty("mensajeError")
        String messageError;
    }
}
