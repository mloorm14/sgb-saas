package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoriaDanoDTO;
import com.uteq.backend.entity.CategoriaDano;
import com.uteq.backend.repository.CategoriaDanoRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias-dano")
public class CategoriaDanoController {

    private final CategoriaDanoRepository repo;

    public CategoriaDanoController(CategoriaDanoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<List<CategoriaDanoDTO>> listar() {
        return ResponseEntity.ok(repo.findAll().stream().map(c -> new CategoriaDanoDTO(c.getId(), c.getNombre())).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaDanoDTO> crear(@RequestBody CategoriaRequest req) {
        if (req.nombre() == null || req.nombre().isBlank()) return ResponseEntity.badRequest().build();
        if (repo.findByNombre(req.nombre()).isPresent()) return ResponseEntity.unprocessableEntity().build();
        CategoriaDano c = new CategoriaDano();
        c.setNombre(req.nombre().trim());
        CategoriaDano g = repo.save(c);
        return ResponseEntity.created(URI.create("/api/v1/categorias-dano/" + g.getId())).body(new CategoriaDanoDTO(g.getId(), g.getNombre()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaDanoDTO> actualizar(@PathVariable Integer id, @RequestBody CategoriaRequest req) {
        CategoriaDano c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setNombre(req.nombre().trim());
        CategoriaDano g = repo.save(c);
        return ResponseEntity.ok(new CategoriaDanoDTO(g.getId(), g.getNombre()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        CategoriaDano c = repo.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setActivo(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }

    public record CategoriaRequest(@NotBlank String nombre) {}
}
