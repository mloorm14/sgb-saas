package com.uteq.backend.controller;

import com.uteq.backend.dto.FavoritoResponseDTO;
import com.uteq.backend.service.FavoritoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.List;

// Módulo 9.2 del roadmap. Solo LECTOR: favoritos es un concepto de
// "mi propia lista", BIBLIOTECARIO/GERENTE/ADMIN no marcan libros propios
// desde acá (a diferencia de PrestamoController, donde varios roles
// operan sobre préstamos ajenos). El usuarioId nunca viaja en la URL/body:
// siempre se resuelve del Authentication en FavoritoService, así que no
// hace falta un endpoint "de otro usuario" ni su chequeo de acceso.
@RestController
@RequestMapping("/api/v1/favoritos")
public class FavoritoController {

    private final FavoritoService favoritoService;

    public FavoritoController(FavoritoService favoritoService) {
        this.favoritoService = favoritoService;
    }

    // ── POST /api/v1/favoritos/{libroId} ──────────────────
    @PostMapping("/{libroId}")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<FavoritoResponseDTO> agregar(
            @PathVariable Long libroId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoritoService.agregar(libroId, authentication));
    }

    // ── DELETE /api/v1/favoritos/{libroId} ────────────────
    @DeleteMapping("/{libroId}")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Void> quitar(
            @PathVariable Long libroId, Authentication authentication) {
        favoritoService.quitar(libroId, authentication);
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
    @GetMapping
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Page<FavoritoResponseDTO>> listarPropios(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "agregadoEn") Pageable pageable) {
        return ResponseEntity.ok(favoritoService.listarPropiosPaginado(authentication, pageable));
    }

    @GetMapping("/todo")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<List<FavoritoResponseDTO>> listarPropiosTodo(Authentication authentication) {
        return ResponseEntity.ok(favoritoService.listarPropios(authentication));
    }
}
