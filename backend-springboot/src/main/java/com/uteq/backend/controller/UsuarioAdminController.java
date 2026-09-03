package com.uteq.backend.controller;

import com.uteq.backend.dto.CambioEstadoUsuarioRequestDTO;
import com.uteq.backend.dto.CambioRolRequestDTO;
import com.uteq.backend.dto.CrearUsuarioAdminRequestDTO;
import com.uteq.backend.dto.UsuarioListadoResponseDTO;
import com.uteq.backend.dto.UsuarioResponseDTO;
import com.uteq.backend.service.UsuarioAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Panel de administración de usuarios (Módulo 5). El listado es accesible
 * también para GERENTE (necesita ver el padrón de usuarios para su
 * operación diaria), pero cambiar rol o estado queda restringido a ADMIN
 * -- ver {@code docs/adr/adr-014-separacion-admin-gerente.md}.
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    // ── GET /api/v1/admin/usuarios?filtro=&page=&size= ────
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Page<UsuarioListadoResponseDTO>> listar(
            @RequestParam(required = false) String filtro,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(usuarioAdminService.listar(filtro, pageable));
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/rol ─────────────
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambioRolRequestDTO dto,
            Authentication authentication) {
        usuarioAdminService.cambiarRol(id, dto.nuevoRol(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/estado ──────────
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoUsuarioRequestDTO dto,
            Authentication authentication) {
        usuarioAdminService.cambiarEstado(id, dto.nuevoEstado(), dto.motivo(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/v1/admin/usuarios ──────────
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponseDTO> crear(@Valid @RequestBody CrearUsuarioAdminRequestDTO dto, Authentication authentication) {
        UsuarioResponseDTO creado = usuarioAdminService.crearUsuario(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // ── DELETE /api/v1/admin/usuarios/{id} soft INACTIVO ──────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam(required = false) String motivo, Authentication authentication) {
        usuarioAdminService.eliminarUsuario(id, motivo, authentication);
        return ResponseEntity.noContent().build();
    }
}
