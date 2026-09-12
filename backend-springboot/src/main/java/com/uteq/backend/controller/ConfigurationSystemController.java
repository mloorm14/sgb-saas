package com.uteq.backend.controller;

import com.uteq.backend.dto.ConfigurationSystemRequestDTO;
import com.uteq.backend.dto.ConfigurationSystemResponseDTO;
import com.uteq.backend.service.ConfigurationSystemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Solo ADMIN: separa quién administra parámetros del sistema (ADMIN)
// de quién opera el día a día (GERENTE).
@RestController
@RequestMapping("/api/v1/configuracion")
@PreAuthorize("hasRole('ADMIN')")
public class ConfigurationSystemController {

    private final ConfigurationSystemService service;

    public ConfigurationSystemController(ConfigurationSystemService service) {
        this.service = service;
    }

    @GetMapping
    /**
     * Lists configuration sistema.
     *
     * @return response entity<list<configuracion sistema response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<ConfigurationSystemResponseDTO>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PutMapping("/{clave}")
    /**
     * Updates Response Entity&lt;Configuracion Sistema Response DTO>.
     *
     * @param key text value used to scope this Response Entity&lt;Configuracion Sistema Response DTO>
     * @param dto configuration Sistema Request data transfer object used to scope this Response Entity&lt;Configuracion Sistema Response DTO>
     * @return Response Entity&lt;Configuracion Sistema Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<ConfigurationSystemResponseDTO> update(
            @PathVariable("clave") String key,
            @Valid @RequestBody ConfigurationSystemRequestDTO dto) {
        return ResponseEntity.ok(service.update(key, dto.value()));
    }
}
