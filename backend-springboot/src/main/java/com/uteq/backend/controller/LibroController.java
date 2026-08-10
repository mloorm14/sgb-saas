package com.uteq.backend.controller;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.service.LibroService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/libros")
@Validated
public class LibroController {

    private final LibroService libroService;

    public LibroController(LibroService libroService) {
        this.libroService = libroService;
    }

    // ── GET /api/v1/libros?page=0&size=10 ────────────────
    // Módulo 9.1: ?categoriaId= y ?autorId= son mutuamente excluyentes por
    // simplicidad (combinar ambos filtros a la vez no está en el alcance
    // de esta rama); si llegan los dos, categoriaId gana.
    @GetMapping
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<LibroResponseDTO>> listar(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) Long autorId,
            @PageableDefault(size = 10, sort = "titulo") Pageable pageable) {
        if (categoriaId != null) {
            return ResponseEntity.ok(libroService.listarPorCategoria(categoriaId, pageable));
        }
        if (autorId != null) {
            return ResponseEntity.ok(libroService.listarPorAutor(autorId, pageable));
        }
        return ResponseEntity.ok(libroService.listar(pageable));
    }

    // ── GET /api/v1/libros/sugerencias?texto= ─────────────
    // Módulo 3 (RF-09/CU-08): autocompletado de catálogo. isAuthenticated()
    // sin restricción de rol -- cualquier usuario logueado puede buscar,
    // a diferencia del resto de endpoints de este controller.
    @GetMapping("/sugerencias")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LibroSugerenciaDTO>> sugerencias(
            @RequestParam @Size(min = 2, max = 60, message = "El texto de búsqueda debe tener entre 2 y 60 caracteres") String texto) {
        return ResponseEntity.ok(libroService.sugerir(texto));
    }

    // ── GET /api/v1/libros/{id} ───────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<LibroResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(libroService.buscarPorId(id));
    }

    // ── POST /api/v1/libros ───────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<LibroResponseDTO> crear(
            @Valid @RequestBody LibroRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(libroService.crear(dto));
    }

    // ── PUT /api/v1/libros/{id} ───────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<LibroResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody LibroRequestDTO dto) {
        return ResponseEntity.ok(libroService.actualizar(id, dto));
    }

    // ── DELETE /api/v1/libros/{id} ────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        libroService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}