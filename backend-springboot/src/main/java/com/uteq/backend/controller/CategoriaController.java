package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoriaRequestDTO;
import com.uteq.backend.dto.CategoriaResponseDTO;
import com.uteq.backend.entity.Categoria;
import com.uteq.backend.repository.CategoriaRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {
        List<CategoriaResponseDTO> categorias = categoriaRepository.findAll().stream()
                .map(c -> new CategoriaResponseDTO(c.getId(), c.getNombre()))
                .toList();
        return ResponseEntity.ok(categorias);
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<CategoriaResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(
                categoriaRepository.findTop5ByNombreContainingIgnoreCase(q).stream()
                        .map(c -> new CategoriaResponseDTO(c.getId(), c.getNombre()))
                        .toList());
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO dto) {
        if (categoriaRepository.existsByNombreIgnoreCase(dto.nombre())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(dto.nombre());
        Categoria guardada = categoriaRepository.save(categoria);
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + guardada.getId()))
                .body(new CategoriaResponseDTO(guardada.getId(), guardada.getNombre()));
    }
}
