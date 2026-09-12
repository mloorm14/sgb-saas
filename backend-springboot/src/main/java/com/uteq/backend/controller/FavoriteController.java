package com.uteq.backend.controller;

import com.uteq.backend.dto.FavoriteResponseDTO;
import com.uteq.backend.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;

// Favoritos del propio LECTOR; el usuarioId se resuelve del
// Authentication en FavoritoService.
@RestController
@RequestMapping("/api/v1/favoritos")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    // ── POST /api/v1/favoritos/{libroId} ──────────────────
    @PostMapping("/{libroId}")
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Procesa agregar y devuelve el resultado calculado por el backend.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<FavoriteResponseDTO> agregar(
            @PathVariable("libroId") Long bookId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoriteService.agregar(bookId, authentication));
    }

    // ── DELETE /api/v1/favoritos/{libroId} ────────────────
    @DeleteMapping("/{libroId}")
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Procesa quitar y devuelve el resultado calculado por el backend.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> quitar(
            @PathVariable("libroId") Long bookId, Authentication authentication) {
        favoriteService.quitar(bookId, authentication);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/v1/favoritos ──────────────────────────────
    // Roadmap original proponía /favoritos/usuario/{usuarioId}, pero el
    // usuarioId ya se resuelve del Authentication (ver FavoritoService) --
    // exponerlo también en el path permitiría a un LECTOR intentar leer
    // favoritos ajenos con solo cambiar el número en la URL, mismo tipo de
    // hallazgo IDOR que ya se corrigió en PrestamoService.validarAccesoUsuario.
    // Se deja sin path param a propósito: "mis favoritos", no "favoritos
    // de tal usuarioId".
    /**
     * Consulta list owns usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Page<FavoriteResponseDTO>> listOwns(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "agregado") Pageable pageable) {
        return ResponseEntity.ok(favoriteService.listOwnsPaginated(authentication, pageable));
    }

    @GetMapping("/todo")
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Consulta list owns todo usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<List<FavoriteResponseDTO>> listOwnsAll(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.listOwns(authentication));
    }
}
