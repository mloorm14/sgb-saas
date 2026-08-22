package com.uteq.backend.controller;

import com.uteq.backend.dto.AutorRequestDTO;
import com.uteq.backend.dto.AutorResponseDTO;
import com.uteq.backend.entity.Autor;
import com.uteq.backend.repository.AutorRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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

    @GetMapping("/buscar")
    public ResponseEntity<List<AutorResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(
                autorRepository.findTop5ByNombreContainingIgnoreCase(q).stream()
                        .map(a -> new AutorResponseDTO(a.getId(), a.getNombre()))
                        .toList());
    }

    @PostMapping
    public ResponseEntity<AutorResponseDTO> crear(@Valid @RequestBody AutorRequestDTO dto) {
        Autor autor = new Autor();
        autor.setNombre(dto.nombre());
        Autor guardado = autorRepository.save(autor);
        return ResponseEntity.created(URI.create("/api/v1/autores/" + guardado.getId()))
                .body(new AutorResponseDTO(guardado.getId(), guardado.getNombre()));
    }
}
