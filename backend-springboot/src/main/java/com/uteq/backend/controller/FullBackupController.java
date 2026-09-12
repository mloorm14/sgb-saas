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
     * Actualiza update config con las reglas de negocio requeridas por el flujo.
     *
     * @param req datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<ConfigurationBackup> updateConfig(@RequestBody ConfigRequestDTO req) {
        return ResponseEntity.ok(service.updateConfiguration(req.frequencyTimes, req.daysRetention, req.enabled));
    }

    // ── Historial de registros ─────────────────────────────────────────────────
    /**
     * Consulta list registrations usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param type criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
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
     * Elimina o anula delete registration despues de validar que la operacion sea permitida.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> deleteRegistration(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/registros/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Genera o entrega download registration a partir de los datos actuales del sistema.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
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
     * Registra register start validando los datos de entrada antes de persistir cambios.
     *
     * @param req datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<RegistrationBackup> registerStart(@RequestBody RegistrationStartDTO req) {
        return ResponseEntity.ok(service.registerStart(req.type, req.executedBy));
    }

    @PutMapping("/registros/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Registra register result validando los datos de entrada antes de persistir cambios.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param req datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<RegistrationBackup> registerResult(
            @PathVariable Long id, @RequestBody RegistrationResultDTO req) {
        return ResponseEntity.ok(service.registerResult(
                id, req.status, req.nameFile, req.sizeFileBytes, req.pathR2, req.messageError));
    }

    // ── Proxy hacia el microservicio Node.js ───────────────────────────────────
    /**
     * Procesa trigger backup full y devuelve el resultado calculado por el backend.
     *
     * @param principal identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
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
