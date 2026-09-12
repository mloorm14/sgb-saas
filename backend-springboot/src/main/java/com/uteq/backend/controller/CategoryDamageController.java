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
     * Creates Response Entity&lt;Categoria damage report DTO>.
     *
     * @param req category Request used to scope this Response Entity&lt;Categoria damage report DTO>
     * @return Response Entity&lt;Categoria damage report DTO> reflecting the state after the operation
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
     * Updates Response Entity&lt;Categoria damage report DTO>.
     *
     * @param id numeric value used to scope this Response Entity&lt;Categoria damage report DTO>
     * @param req category Request used to scope this Response Entity&lt;Categoria damage report DTO>
     * @return Response Entity&lt;Categoria damage report DTO> reflecting the state after the operation
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
     * Deletes Response Entity&lt;Void>.
     *
     * @param id numeric value used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        CategoryDamage c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setActive(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }

    public record CategoryRequest(@NotBlank @JsonProperty("nombre") String name) {}
}
