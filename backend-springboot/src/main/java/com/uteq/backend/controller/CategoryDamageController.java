package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoryDamageDTO;
import com.uteq.backend.entity.CategoryDamage;
import com.uteq.backend.repository.CategoryDamageRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias-dano")
public class CategoryDamageController {

    private final CategoryDamageRepository repo;

    public CategoryDamageController(CategoryDamageRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Lists category damage report.
     *
     * @return response entity<list<categoria damage report dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<CategoryDamageDTO>> list() {
        return ResponseEntity.ok(repo.findAll().stream().map(c -> new CategoryDamageDTO(c.getId(), c.getName())).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param req datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<CategoryDamageDTO> create(@RequestBody CategoryRequest req) {
        if (req.name() == null || req.name().isBlank()) return ResponseEntity.badRequest().build();
        if (repo.findByName(req.name()).isPresent()) return ResponseEntity.unprocessableEntity().build();
        CategoryDamage c = new CategoryDamage();
        c.setName(req.name().trim());
        CategoryDamage g = repo.save(c);
        return ResponseEntity.created(URI.create("/api/v1/categorias-dano/" + g.getId())).body(new CategoryDamageDTO(g.getId(), g.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Actualiza update con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param req datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<CategoryDamageDTO> update(@PathVariable Integer id, @RequestBody CategoryRequest req) {
        CategoryDamage c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setName(req.name().trim());
        CategoryDamage g = repo.save(c);
        return ResponseEntity.ok(new CategoryDamageDTO(g.getId(), g.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    /**
     * Elimina o anula delete despues de validar que la operacion sea permitida.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        CategoryDamage c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setActive(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }
    /**
     * Procesa category request y devuelve el resultado calculado por el backend.
     *
     * @param name valor de entrada name usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */

    public record CategoryRequest(@NotBlank @JsonProperty("nombre") String name) {}
}
