package com.uteq.backend.controller;

import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.service.BookService;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Fachada pública de SOLO LECTURA del catálogo (portal público sin cuenta,
 * regla de negocio de la Rama C: cualquier persona puede buscar/ver el
 * catálogo en tiempo real; reservar, favoritos y cuenta requieren login).
 * <p>
 * El acceso lo habilita {@code SecurityConfig} con {@code permitAll()} sobre
 * {@code /api/publico/**}: este controller NO lleva {@code @PreAuthorize} en
 * ningún método (el filtro de seguridad ya los deja pasar). Es una fachada
 * que delega en {@link BookService} — misma firma/lógica que
 * {@code LibroController} para {@code /api/v1/libros}, sin exponer nada que
 * ese método ya no exponga. No existe ningún POST/PUT/DELETE acá a propósito:
 * la superficie pública es angosta y de solo lectura (ver
 * {@code PublicoLibroControllerTest}).
 * <p>
 * El endpoint {@code /sugerencias} reusa el autocompletado del catálogo
 * autenticado: el buscador del portal necesita búsqueda por título, y el DTO
 * {@link BookSuggestionDTO} solo expone id/titulo/disponible.
 */
@RestController
@RequestMapping("/api/publico/libros")
@RequiredArgsConstructor
@Validated
public class PublicBookController {

    private final BookService bookService;

    // ── GET /api/publico/libros?q=&categoriaId=&autorId=&page= ──────
    // Espejo de LibroController.listar(): q busca por título/ISBN,
    // categoriaId/autorId filtran (mutuamente excluyentes), paginado
    // por defecto size=10 sort=titulo.
    @GetMapping
    public Page<BookResponseDTO> list(
            @RequestParam(required = false) String q,
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @RequestParam(name = "autorId", required = false) Long authorId,
            @RequestParam(name = "disponible", required = false) Boolean available,
            @PageableDefault(size = 10, sort = "title") Pageable pageable) {
        return bookService.listWithFilters(q, null, categoryId, authorId, available, pageable);
    }

    // ── GET /api/publico/libros/sugerencias?texto= ───────────────────
    // Autocompletado del buscador público. Misma validación que el endpoint
    // autenticado (mínimo 2 caracteres).
    @GetMapping("/sugerencias")
    public List<BookSuggestionDTO> suggestions(
            @RequestParam("texto") @Size(min = 2, max = 60, message = "El texto de búsqueda debe tener entre 2 y 60 caracteres") String text) {
        return bookService.sugerir(text);
    }

    // ── GET /api/publico/libros/{id} ─────────────────────────────────
    @GetMapping("/{id}")
    /**
     * Retrieves book Response data transfer object.
     *
     * @param id numeric identifier used to scope this book Response data transfer object
     * @return book Response data transfer object reflecting the state after the operation
     */
    public BookResponseDTO get(@PathVariable Long id) {
        return bookService.searchByIdPublic(id);
    }

    // ── GET /api/publico/libros/{id}/portada ─────────────────────────
    // Portada servida sin JWT a propósito: un <img src> del portal público
    // no puede mandar header Authorization. La imagen en sí no es dato
    // sensible (misma decisión que el cartel de portada del catálogo
    // autenticado). Mismo armado de ResponseEntity que el endpoint
    // autenticado de LibroController: Content-Type dinámico según
    // portada_tipo, 404 si el libro no existe o no tiene portada.
    @GetMapping("/{id}/portada")
    /**
     * Handles cover image.
     *
     * @param id numeric identifier used to scope this cover image
     * @return Response Entity&lt;byte[]> reflecting the state after the operation
     */
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        CoverImageDTO cover = bookService.getCover(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .body(cover.bytes());
    }
}