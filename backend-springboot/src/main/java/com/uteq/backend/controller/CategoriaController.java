package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoriaResponseDTO;
import com.uteq.backend.repository.CategoriaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}