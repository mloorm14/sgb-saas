package com.uteq.backend.controller;

import com.uteq.backend.dto.SupplierRequestDTO;
import com.uteq.backend.dto.SupplierResponseDTO;
import com.uteq.backend.entity.Supplier;
import com.uteq.backend.repository.SupplierRepository;
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
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SupplierResponseDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(name = "activo", required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        String filter = (q != null && !q.isBlank()) ? q.trim() : null;
        Boolean activeFilter = active;
        // Si no hay filtros, usar findAll paginado (mas eficiente)
        if (filter == null && activeFilter == null) {
            return ResponseEntity.ok(supplierRepository.findAll(pageable).map(this::toDTO));
        }
        Page<SupplierResponseDTO> page = supplierRepository.searchWithFilters(filter, activeFilter, pageable).map(this::toDTO);
        return ResponseEntity.ok(page);
    }

    // Compatibilidad: lista completa para casos antiguos (no usar con 50k)
    @GetMapping("/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Lists todo.
     *
     * @return response entity<list<proveedor response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<SupplierResponseDTO>> listTodo() {
        List<SupplierResponseDTO> suppliers = supplierRepository.findAll().stream()
                .map(this::toDTO).toList();
        return ResponseEntity.ok(suppliers);
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Searches Response Entity&lt;List<Proveedor Response DTO>>.
     *
     * @param q text value used to scope this Response Entity&lt;List<Proveedor Response DTO>>
     * @return Response Entity&lt;List<Proveedor Response DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<SupplierResponseDTO>> search(@RequestParam String q) {
        return ResponseEntity.ok(
                supplierRepository.findTop5ByNameContainingIgnoreCase(q).stream()
                        .map(this::toDTO)
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Creates Response Entity&lt;Proveedor Response DTO>.
     *
     * @param dto supplier Request data transfer object used to scope this Response Entity&lt;Proveedor Response DTO>
     * @return Response Entity&lt;Proveedor Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<SupplierResponseDTO> create(@Valid @RequestBody SupplierRequestDTO dto) {
        if (supplierRepository.existsByNameIgnoreCase(dto.name())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        if (dto.ruc() != null && !dto.ruc().isBlank() && supplierRepository.existsByRucIgnoreCase(dto.ruc())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Supplier p = new Supplier();
        p.setName(dto.name());
        p.setRuc(dto.ruc());
        p.setDireccion(dto.direccion());
        p.setTelefono(dto.telefono());
        p.setEmail(dto.email());
        p.setPersonaContacto(dto.personaContacto());
        p.setActive(dto.active() != null ? dto.active() : true);
        Supplier guardado = supplierRepository.save(p);
        return ResponseEntity.created(URI.create("/api/v1/proveedores/" + guardado.getId()))
                .body(toDTO(guardado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Updates Response Entity&lt;Proveedor Response DTO>.
     *
     * @param id numeric value used to scope this Response Entity&lt;Proveedor Response DTO>
     * @param dto supplier Request data transfer object used to scope this Response Entity&lt;Proveedor Response DTO>
     * @return Response Entity&lt;Proveedor Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<SupplierResponseDTO> update(@PathVariable Integer id,
                                                           @Valid @RequestBody SupplierRequestDTO dto) {
        return supplierRepository.findById(id)
                .map(existing -> {
                    existing.setName(dto.name());
                    existing.setRuc(dto.ruc());
                    existing.setDireccion(dto.direccion());
                    existing.setTelefono(dto.telefono());
                    existing.setEmail(dto.email());
                    existing.setPersonaContacto(dto.personaContacto());
                    if (dto.active() != null) existing.setActive(dto.active());
                    Supplier guardado = supplierRepository.save(existing);
                    return ResponseEntity.ok(toDTO(guardado));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private SupplierResponseDTO toDTO(Supplier p) {
        return new SupplierResponseDTO(
                p.getId(), p.getName(), p.getRuc(), p.getDireccion(),
                p.getTelefono(), p.getEmail(), p.getPersonaContacto(), p.getActive());
    }
}
