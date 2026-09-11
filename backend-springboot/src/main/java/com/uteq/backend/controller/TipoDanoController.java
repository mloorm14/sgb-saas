package com.uteq.backend.controller;

import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.service.TipoDanoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tipos-dano")
@PreAuthorize("hasRole('ADMIN')")
public class TipoDanoController {

    private final TipoDanoService tipoDanoService;

    public TipoDanoController(TipoDanoService tipoDanoService) {
        this.tipoDanoService = tipoDanoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Executes the listar operation.
     * @return operation result
     */
    public ResponseEntity<List<TipoDanoDTO>> listar() {
        return ResponseEntity.ok(tipoDanoService.listarTodos());
    }

    @PostMapping
    /**
     * Executes the crear operation.
     * @param dto value required by the operation
     * @return operation result
     */
    public ResponseEntity<TipoDanoDTO> crear(@Valid @RequestBody TipoDanoRequestDTO dto) {
        TipoDanoDTO creado = tipoDanoService.crear(dto.nombre(), dto.categoriaId(), dto.tipoCosto(), dto.valor());
        return ResponseEntity.created(URI.create("/api/v1/tipos-dano/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    /**
     * Executes the actualizar operation.
     * @param id value required by the operation
     * @param dto value required by the operation
     * @return operation result
     */
    public ResponseEntity<TipoDanoDTO> actualizar(@PathVariable Integer id, @Valid @RequestBody TipoDanoRequestDTO dto) {
        return ResponseEntity.ok(tipoDanoService.actualizar(id, dto.nombre(), dto.categoriaId(), dto.tipoCosto(), dto.valor()));
    }

    @DeleteMapping("/{id}")
    /**
     * Executes the eliminar operation.
     * @param id value required by the operation
     * @return operation result
     */
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        tipoDanoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    public record TipoDanoRequestDTO(
            @NotBlank String nombre,
            @NotNull Integer categoriaId,
            @NotBlank String tipoCosto,
            @NotNull @DecimalMin("0") BigDecimal valor
    ) {}
}
