package com.uteq.backend.controller;

import com.uteq.backend.dto.ConfiguracionSistemaRequestDTO;
import com.uteq.backend.dto.ConfiguracionSistemaResponseDTO;
import com.uteq.backend.service.ConfiguracionSistemaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Solo ADMIN (Modulo 9.4/5.3 del roadmap: separacion entre quien administra
// permisos/parametros del sistema -ADMIN- y quien administra la operacion
// diaria -GERENTE-, ver Modulo 5).
@RestController
@RequestMapping("/api/v1/configuracion")
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracionSistemaController {

    private final ConfiguracionSistemaService service;

    public ConfiguracionSistemaController(ConfiguracionSistemaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ConfiguracionSistemaResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PutMapping("/{clave}")
    public ResponseEntity<ConfiguracionSistemaResponseDTO> actualizar(
            @PathVariable String clave,
            @Valid @RequestBody ConfiguracionSistemaRequestDTO dto) {
        return ResponseEntity.ok(service.actualizar(clave, dto.valor()));
    }
}
