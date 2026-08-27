package com.uteq.backend.controller;

import com.uteq.backend.dto.EditorialRequestDTO;
import com.uteq.backend.dto.EditorialResponseDTO;
import com.uteq.backend.entity.Editorial;
import com.uteq.backend.repository.EditorialRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    @GetMapping("/buscar")
    public ResponseEntity<List<EditorialResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(
                editorialRepository.findTop5ByNombreContainingIgnoreCase(q).stream()
                        .map(e -> new EditorialResponseDTO(e.getId(), e.getNombre()))
                        .toList());
    }

    @PostMapping
    public ResponseEntity<EditorialResponseDTO> crear(@Valid @RequestBody EditorialRequestDTO dto) {
        if (editorialRepository.existsByNombreIgnoreCase(dto.nombre())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Editorial e = new Editorial();
        e.setNombre(dto.nombre());
        Editorial guardada = editorialRepository.save(e);
        return ResponseEntity.created(URI.create("/api/v1/editoriales/" + guardada.getId()))
                .body(new EditorialResponseDTO(guardada.getId(), guardada.getNombre()));
    }
}