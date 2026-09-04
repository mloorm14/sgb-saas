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
 * Panel de administración de usuarios (Módulo 5 + F8-gerente/V38). El
 * listado es ADMIN/GERENTE (GERENTE con ?mios=true ve solo sus creados);
 * crear/cambiar-rol/cambiar-estado admiten GERENTE con recorte a
 * LECTOR/BIBLIOTECARIO + ACTIVO/INACTIVO sobre sus creados (el service lo
 * verifica). Solo ADMIN crea GERENTE/ADMIN, ve todo y elimina (soft).
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    // ── GET /api/v1/admin/usuarios?filtro=&page=&size=&mios= ────
    // F8-gerente: ?mios=true filtra por creado_por propio (el service además
    // fuerza ese filtro para GERENTE aunque no mande el flag).
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Page<UsuarioListadoResponseDTO>> listar(
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false, defaultValue = "false") boolean mios,
            @PageableDefault(size = 10, sort = "id") Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(usuarioAdminService.listar(filtro, pageable, authentication, mios));
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/rol ─────────────
    // F8-gerente: GERENTE limitado en service a sus creados + LECTOR/BIBLIOTECARIO.
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Void> cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambioRolRequestDTO dto,
            Authentication authentication) {
        usuarioAdminService.cambiarRol(id, dto.nuevoRol(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/estado ──────────
    // F8-gerente: GERENTE limitado en service a sus creados + ACTIVO/INACTIVO.
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoUsuarioRequestDTO dto,
            Authentication authentication) {
        usuarioAdminService.cambiarEstado(id, dto.nuevoEstado(), dto.motivo(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/v1/admin/usuarios ──────────
    // F8-gerente: GERENTE crea solo LECTOR/BIBLIOTECARIO (service lo verifica).
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
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
