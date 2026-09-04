package com.uteq.backend.controller;

import com.uteq.backend.dto.ProveedorRequestDTO;
import com.uteq.backend.dto.ProveedorResponseDTO;
import com.uteq.backend.entity.Proveedor;
import com.uteq.backend.repository.ProveedorRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/proveedores")
public class ProveedorController {

    private final ProveedorRepository proveedorRepository;

    public ProveedorController(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ProveedorResponseDTO>> listar(
            @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
        Page<ProveedorResponseDTO> page = proveedorRepository.findAll(pageable).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    // Compatibilidad: lista completa para casos antiguos (no usar con 50k)
    @GetMapping("/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ProveedorResponseDTO>> listarTodo() {
        List<ProveedorResponseDTO> proveedores = proveedorRepository.findAll().stream()
                .map(this::toDTO).toList();
        return ResponseEntity.ok(proveedores);
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ProveedorResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(
                proveedorRepository.findTop5ByNombreContainingIgnoreCase(q).stream()
                        .map(this::toDTO)
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<ProveedorResponseDTO> crear(@Valid @RequestBody ProveedorRequestDTO dto) {
        if (proveedorRepository.existsByNombreIgnoreCase(dto.nombre())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        if (dto.ruc() != null && !dto.ruc().isBlank() && proveedorRepository.existsByRucIgnoreCase(dto.ruc())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Proveedor p = new Proveedor();
        p.setNombre(dto.nombre());
        p.setRuc(dto.ruc());
        p.setDireccion(dto.direccion());
        p.setTelefono(dto.telefono());
        p.setEmail(dto.email());
        p.setPersonaContacto(dto.personaContacto());
        p.setActivo(dto.activo() != null ? dto.activo() : true);
        Proveedor guardado = proveedorRepository.save(p);
        return ResponseEntity.created(URI.create("/api/v1/proveedores/" + guardado.getId()))
                .body(toDTO(guardado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<ProveedorResponseDTO> actualizar(@PathVariable Integer id,
                                                           @Valid @RequestBody ProveedorRequestDTO dto) {
        return proveedorRepository.findById(id)
                .map(existente -> {
                    existente.setNombre(dto.nombre());
                    existente.setRuc(dto.ruc());
                    existente.setDireccion(dto.direccion());
                    existente.setTelefono(dto.telefono());
                    existente.setEmail(dto.email());
                    existente.setPersonaContacto(dto.personaContacto());
                    if (dto.activo() != null) existente.setActivo(dto.activo());
                    Proveedor guardado = proveedorRepository.save(existente);
                    return ResponseEntity.ok(toDTO(guardado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ProveedorResponseDTO toDTO(Proveedor p) {
        return new ProveedorResponseDTO(
                p.getId(), p.getNombre(), p.getRuc(), p.getDireccion(),
                p.getTelefono(), p.getEmail(), p.getPersonaContacto(), p.getActivo());
    }
}
