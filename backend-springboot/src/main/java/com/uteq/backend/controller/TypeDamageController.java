package com.uteq.backend.controller;

import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.service.TypeDamageService;
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
public class TypeDamageController {

    private final TypeDamageService typeDamageService;

    public TypeDamageController(TypeDamageService typeDamageService) {
        this.typeDamageService = typeDamageService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Lists tipo damage report.
     *
     * @return response entity<list<tipo damage report dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<TypeDamageDTO>> list() {
        return ResponseEntity.ok(typeDamageService.listAll());
    }

    @PostMapping
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<TypeDamageDTO> create(@Valid @RequestBody TypeDamageRequestDTO dto) {
        TypeDamageDTO created = typeDamageService.create(dto.name(), dto.categoryId(), dto.typeCost(), dto.value());
        return ResponseEntity.created(URI.create("/api/v1/tipos-dano/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    /**
     * Actualiza update con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<TypeDamageDTO> update(@PathVariable Integer id, @Valid @RequestBody TypeDamageRequestDTO dto) {
        return ResponseEntity.ok(typeDamageService.update(id, dto.name(), dto.categoryId(), dto.typeCost(), dto.value()));
    }

    @DeleteMapping("/{id}")
    /**
     * Elimina o anula delete despues de validar que la operacion sea permitida.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        typeDamageService.delete(id);
        return ResponseEntity.noContent().build();
    }
    /**
     * Procesa type damage request dto y devuelve el resultado calculado por el backend.
     *
     * @param name valor de entrada name usado por la operacion para completar su regla de negocio
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param typeCost valor de entrada typeCost usado por la operacion para completar su regla de negocio
     * @param value clave o valor de configuracion que se valida antes de guardarse
     */

    public record TypeDamageRequestDTO(
            @NotBlank String name,
            @NotNull Integer categoryId,
            @NotBlank String typeCost,
            @NotNull @DecimalMin("0") BigDecimal value
    ) {}
}
