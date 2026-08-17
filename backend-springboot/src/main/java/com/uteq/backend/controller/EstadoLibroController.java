package com.uteq.backend.controller;

import com.uteq.backend.dto.EstadoLibroResponseDTO;
import com.uteq.backend.repository.EstadoLibroRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catálogo estados_libro (FIX 3): los <select> del formulario de libros
// necesitan listar estados. Mismo patrón que CategoriaController/
// AutorController (GET sin @PreAuthorize, array plano sin paginación).
@RestController
@RequestMapping("/api/v1/estados-libro")
public class EstadoLibroController {

    private final EstadoLibroRepository estadoLibroRepository;

    public EstadoLibroController(EstadoLibroRepository estadoLibroRepository) {
        this.estadoLibroRepository = estadoLibroRepository;
    }

    @GetMapping
    public ResponseEntity<List<EstadoLibroResponseDTO>> listar() {
        List<EstadoLibroResponseDTO> estados = estadoLibroRepository.findAll().stream()
                .map(e -> new EstadoLibroResponseDTO(e.getId(), e.getNombre()))
                .toList();
        return ResponseEntity.ok(estados);
    }
}