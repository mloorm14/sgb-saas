package com.uteq.backend.controller;

import com.uteq.backend.dto.IdiomaResponseDTO;
import com.uteq.backend.repository.IdiomaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catálogo idiomas (FIX 3): los <select> del formulario de libros
// necesitan listar idiomas. Mismo patrón que CategoriaController/
// AutorController (GET sin @PreAuthorize, array plano sin paginación).
@RestController
@RequestMapping("/api/v1/idiomas")
public class IdiomaController {

    private final IdiomaRepository idiomaRepository;

    public IdiomaController(IdiomaRepository idiomaRepository) {
        this.idiomaRepository = idiomaRepository;
    }

    @GetMapping
    public ResponseEntity<List<IdiomaResponseDTO>> listar() {
        List<IdiomaResponseDTO> idiomas = idiomaRepository.findAll().stream()
                .map(i -> new IdiomaResponseDTO(i.getId(), i.getNombre()))
                .toList();
        return ResponseEntity.ok(idiomas);
    }
}