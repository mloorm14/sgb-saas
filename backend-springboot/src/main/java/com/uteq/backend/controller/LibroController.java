package com.uteq.backend.controller;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.LibroIsbnLookupDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.service.LibroService;
import com.uteq.backend.service.LibroIsbnLookupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/libros")
@Validated
public class LibroController {

    private final LibroService libroService;
    private final LibroIsbnLookupService libroIsbnLookupService;

    public LibroController(LibroService libroService, LibroIsbnLookupService libroIsbnLookupService) {
        this.libroService = libroService;
        this.libroIsbnLookupService = libroIsbnLookupService;
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

    // ── GET /api/v1/libros/lookup-isbn?isbn= ─────────────
    // Módulo inventario (mockup 14): autocompletar desde Google Books.
    // La ruta literal /lookup-isbn gana sobre /{id} (Spring elige el
    // patrón más específico). 404 con ProblemDetail si no hay resultado.
    @GetMapping("/lookup-isbn")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<LibroIsbnLookupDTO> lookupIsbn(
            @RequestParam @Pattern(regexp = "^[0-9\\-]{10,17}$", message = "ISBN inválido")
            @Size(max = 13, message = "El ISBN no puede superar 13 caracteres") String isbn) {
        return ResponseEntity.ok(libroIsbnLookupService.buscarPorIsbn(isbn));
    }

    // ── GET /api/v1/libros/lookup-isbn/portada?isbn= ─────
    // Proxy de la portada de Google Books: el backend descarga el
    // thumbnail y lo devuelve como binario (el navegador no debe llamar
    // a Google Books directo). Igual que /{id}/portada, 404 si no hay.
    @GetMapping("/lookup-isbn/portada")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<byte[]> lookupIsbnPortada(
            @RequestParam @Pattern(regexp = "^[0-9\\-]{10,17}$", message = "ISBN inválido")
            @Size(max = 13, message = "El ISBN no puede superar 13 caracteres") String isbn) {
        PortadaImagenDTO portada = libroIsbnLookupService.obtenerPortada(isbn);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(portada.contentType()))
                .body(portada.bytes());
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

    // ── POST /api/v1/libros/{id}/portada ───────────────────
    // Módulo portada binaria (V13__portada_imagen.sql): multipart con un
    // solo campo "archivo". La validación (tipo/tamaño) vive en
    // LibroService.actualizarPortada y responde 400 con ProblemDetail vía
    // GlobalExceptionHandler, no acá. Admin/Gerente heredan el rol de
    // bibliotecario, mismo criterio que el resto del controller.
    @PostMapping(value = "/{id}/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<LibroResponseDTO> subirPortada(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(libroService.actualizarPortada(id, archivo));
    }

    // ── GET /api/v1/libros/{id}/portada ────────────────────
    // Devuelve el binario con Content-Type dinámico según portada_tipo
    // (image/png|image/jpeg|image/webp). LECTURA para todos los roles
    // autenticados, igual que el resto del catálogo. 404 (no un
    // placeholder) si el libro no existe o no tiene portada -- eso es
    // decisión del frontend.
    @GetMapping("/{id}/portada")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<byte[]> obtenerPortada(@PathVariable Long id) {
        PortadaImagenDTO portada = libroService.obtenerPortada(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(portada.contentType()))
                .body(portada.bytes());
    }
}