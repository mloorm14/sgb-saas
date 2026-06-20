package com.uteq.backend.controller;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.service.LibroService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/libros")
public class LibroController {

    private final LibroService libroService;

    public LibroController(LibroService libroService) {
        this.libroService = libroService;
    }

    // ── GET /api/v1/libros?page=0&size=10 ────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<Page<LibroResponseDTO>> listar(
            @PageableDefault(size = 10, sort = "titulo") Pageable pageable) {
        return ResponseEntity.ok(libroService.listar(pageable));
    }

    // ── GET /api/v1/libros/{id} ───────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<LibroResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(libroService.buscarPorId(id));
    }

    // ── POST /api/v1/libros ───────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LibroResponseDTO> crear(
            @Valid @RequestBody LibroRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(libroService.crear(dto));
    }

    // ── PUT /api/v1/libros/{id} ───────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LibroResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody LibroRequestDTO dto) {
        return ResponseEntity.ok(libroService.actualizar(id, dto));
    }

    // ── DELETE /api/v1/libros/{id} ────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        libroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}