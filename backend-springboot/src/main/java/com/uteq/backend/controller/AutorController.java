package com.uteq.backend.controller;

import com.uteq.backend.dto.AutorResponseDTO;
import com.uteq.backend.repository.AutorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/autores")
public class AutorController {

    private final AutorRepository autorRepository;

    public AutorController(AutorRepository autorRepository) {
        this.autorRepository = autorRepository;
    }

    @GetMapping
    public ResponseEntity<List<AutorResponseDTO>> listar() {
        List<AutorResponseDTO> autores = autorRepository.findAll().stream()
                .map(a -> new AutorResponseDTO(a.getId(), a.getNombre()))
                .toList();
        return ResponseEntity.ok(autores);
    }
}