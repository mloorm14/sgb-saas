package com.uteq.backend.controller;

import com.uteq.backend.dto.EditorialResponseDTO;
import com.uteq.backend.repository.EditorialRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Catálogo editoriales (FIX 3): los <select> del formulario de libros
// necesitan listar editoriales. Mismo patrón que CategoriaController/
// AutorController (GET sin @PreAuthorize, array plano sin paginación).
@RestController
@RequestMapping("/api/v1/editoriales")
public class EditorialController {

    private final EditorialRepository editorialRepository;

    public EditorialController(EditorialRepository editorialRepository) {
        this.editorialRepository = editorialRepository;
    }

    @GetMapping
    public ResponseEntity<List<EditorialResponseDTO>> listar() {
        List<EditorialResponseDTO> editoriales = editorialRepository.findAll().stream()
                .map(e -> new EditorialResponseDTO(e.getId(), e.getNombre()))
                .toList();
        return ResponseEntity.ok(editoriales);
    }
}