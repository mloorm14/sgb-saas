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
     * Creates Response Entity&lt;Tipo damage report DTO>.
     *
     * @param dto Tipo damage report Request data transfer object used to scope this Response Entity&lt;Tipo damage report DTO>
     * @return Response Entity&lt;Tipo damage report DTO> reflecting the state after the operation
     */
    public ResponseEntity<TypeDamageDTO> create(@Valid @RequestBody TypeDamageRequestDTO dto) {
        TypeDamageDTO created = typeDamageService.create(dto.name(), dto.categoryId(), dto.typeCost(), dto.value());
        return ResponseEntity.created(URI.create("/api/v1/tipos-dano/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    /**
     * Updates Response Entity&lt;Tipo damage report DTO>.
     *
     * @param id numeric value used to scope this Response Entity&lt;Tipo damage report DTO>
     * @param dto Tipo damage report Request data transfer object used to scope this Response Entity&lt;Tipo damage report DTO>
     * @return Response Entity&lt;Tipo damage report DTO> reflecting the state after the operation
     */
    public ResponseEntity<TypeDamageDTO> update(@PathVariable Integer id, @Valid @RequestBody TypeDamageRequestDTO dto) {
        return ResponseEntity.ok(typeDamageService.update(id, dto.name(), dto.categoryId(), dto.typeCost(), dto.value()));
    }

    @DeleteMapping("/{id}")
    /**
     * Deletes Response Entity&lt;Void>.
     *
     * @param id numeric value used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        typeDamageService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record TypeDamageRequestDTO(
            @NotBlank String name,
            @NotNull Integer categoryId,
            @NotBlank String typeCost,
            @NotNull @DecimalMin("0") BigDecimal value
    ) {}
}
