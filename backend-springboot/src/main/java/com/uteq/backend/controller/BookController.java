package com.uteq.backend.controller;

import com.uteq.backend.dto.BookRequestDTO;
import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.BookIsbnLookupDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.service.BookService;
import com.uteq.backend.service.BookIsbnLookupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
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
public class BookController {

    private final BookService bookService;
    private final BookIsbnLookupService bookIsbnLookupService;

    public BookController(BookService bookService, BookIsbnLookupService bookIsbnLookupService) {
        this.bookService = bookService;
        this.bookIsbnLookupService = bookIsbnLookupService;
    }

    // ── GET /api/v1/libros?page=0&size=10 ────────────────
    // Filtros combinables: q (título/ISBN), estadoLibroId, categoriaId, autorId.
    // Si no se envía estadoLibroId, default = ACTIVO.
    @GetMapping
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<BookResponseDTO>> list(
            @RequestParam(required = false) String q,
            @RequestParam(name = "estadoLibroId", required = false) Integer statusBookId,
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @RequestParam(name = "autorId", required = false) Long authorId,
            @RequestParam(name = "disponible", required = false) Boolean available,
            @PageableDefault(size = 10, sort = "title") Pageable pageable) {
        return ResponseEntity.ok(bookService.listWithFilters(q, statusBookId, categoryId, authorId, available, pageable));
    }

    // ── GET /api/v1/libros/sugerencias?texto= ─────────────
    // Autocompletado de catálogo. Cualquier usuario autenticado puede buscar.
    @GetMapping("/sugerencias")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BookSuggestionDTO>> suggestions(
            @RequestParam("texto") @Size(min = 2, max = 60, message = "El texto de búsqueda debe tener entre 2 y 60 caracteres") String text) {
        return ResponseEntity.ok(bookService.sugerir(text));
    }

    // ── GET /api/v1/libros/pendientes ────────────────
    // Listado de libros en estados de gestión: DADO_DE_BAJA, PENDIENTE, EN_REPARACION, PERDIDO
    // Si no se envía estadoIds, usa los 4 por defecto.
    @GetMapping("/pendientes")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<Page<BookResponseDTO>> pendientes(
            @RequestParam(required = false) String q,
            @RequestParam(name = "anioPublicacion", required = false) Integer yearPublication,
            @RequestParam(name = "estadoIds", required = false) List<Integer> statusIds,
            @PageableDefault(size = 10) @SortDefault(sort = "date_registration", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(bookService.listPendientes(q, yearPublication, statusIds, pageable));
    }

    // ── GET /api/v1/libros/lookup-isbn?isbn= ─────────────
    // Autocompletar desde Google Books. La ruta literal gana sobre /{id};
    // 404 con ProblemDetail si no hay resultado.
    @GetMapping("/lookup-isbn")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<BookIsbnLookupDTO> lookupIsbn(
            @RequestParam @Pattern(regexp = "^[0-9]{10,13}$", message = "ISBN debe tener 10 a 13 dígitos")
            @Size(min = 10, max = 13, message = "El ISBN debe tener entre 10 y 13 caracteres") String isbn) {
        return ResponseEntity.ok(bookIsbnLookupService.searchByIsbn(isbn));
    }

    // ── GET /api/v1/libros/lookup-isbn/portada?isbn= ─────
    // Proxy de la portada de Google Books: el backend descarga el
    // thumbnail y lo devuelve como binario (el navegador no debe llamar
    // a Google Books directo). Igual que /{id}/portada, 404 si no hay.
    @GetMapping("/lookup-isbn/portada")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<byte[]> lookupIsbnCover(
            @RequestParam @Pattern(regexp = "^[0-9]{10,13}$", message = "ISBN debe tener 10 a 13 dígitos")
            @Size(min = 10, max = 13, message = "El ISBN debe tener entre 10 y 13 caracteres") String isbn) {
        CoverImageDTO cover = bookIsbnLookupService.getCover(isbn);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .body(cover.bytes());
    }

    // ── GET /api/v1/libros/{id} ───────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Searches Response Entity&lt;Libro Response DTO>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Libro Response DTO>
     * @return Response Entity&lt;Libro Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<BookResponseDTO> search(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.searchById(id));
    }

    // ── POST /api/v1/libros ───────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Creates Response Entity&lt;Libro Response DTO>.
     *
     * @param dto book Request data transfer object used to scope this Response Entity&lt;Libro Response DTO>
     * @return Response Entity&lt;Libro Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<BookResponseDTO> create(
            @Valid @RequestBody BookRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookService.create(dto));
    }

    // ── PUT /api/v1/libros/{id} ───────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Updates Response Entity&lt;Libro Response DTO>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Libro Response DTO>
     * @param dto book Request data transfer object used to scope this Response Entity&lt;Libro Response DTO>
     * @return Response Entity&lt;Libro Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<BookResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody BookRequestDTO dto) {
        return ResponseEntity.ok(bookService.update(id, dto));
    }

    // ── DELETE /api/v1/libros/{id} ────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Deletes Response Entity&lt;Void>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/v1/libros/{id}/portada ───────────────────
    // Subida multipart con campo "archivo". La validación de tipo/tamaño
    // vive en LibroService y responde 400 vía GlobalExceptionHandler.
    @PostMapping(value = "/{id}/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<BookResponseDTO> uploadCover(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile file) {
        return ResponseEntity.ok(bookService.updateCover(id, file));
    }

    // ── GET /api/v1/libros/{id}/portada ────────────────────
    // Devuelve el binario con Content-Type dinámico según portada_tipo
    // (image/png|image/jpeg|image/webp). LECTURA para todos los roles
    // autenticados, igual que el resto del catálogo. 404 (no un
    // placeholder) si el libro no existe o no tiene portada -- eso es
    // decisión del frontend.
    @GetMapping("/{id}/portada")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Retrieves Response Entity&lt;byte[]>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;byte[]>
     * @return Response Entity&lt;byte[]> reflecting the state after the operation
     */
    public ResponseEntity<byte[]> getCover(@PathVariable Long id) {
        CoverImageDTO cover = bookService.getCover(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .body(cover.bytes());
    }
}