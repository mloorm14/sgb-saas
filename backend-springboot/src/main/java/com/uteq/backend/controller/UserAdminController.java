package com.uteq.backend.controller;

import com.uteq.backend.dto.ChangeStatusUserRequestDTO;
import com.uteq.backend.dto.ChangeRoleRequestDTO;
import com.uteq.backend.dto.CreateUserAdminRequestDTO;
import com.uteq.backend.dto.UserListingResponseDTO;
import com.uteq.backend.dto.UserResponseDTO;
import com.uteq.backend.service.UserAdminService;
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
 * Panel de administración de usuarios. GERENTE opera sobre sus creados;
 * solo ADMIN crea GERENTE/ADMIN, ve todo y elimina (soft).
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    // ── GET /api/v1/admin/usuarios?filtro=&page=&size=&mios= ────
    // F8-gerente: ?mios=true filtra por creado_por propio (el service además
    // fuerza ese filtro para GERENTE aunque no mande el flag).
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Page<UserListingResponseDTO>> list(
            @RequestParam(name = "filtro", required = false) String filter,
            @RequestParam(required = false, defaultValue = "false") boolean mios,
            @PageableDefault(size = 10, sort = "id") Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(userAdminService.list(filter, pageable, authentication, mios));
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/rol ─────────────
    // F8-gerente: GERENTE limitado en service a sus creados + LECTOR/BIBLIOTECARIO.
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    /**
     * Changes Response Entity&lt;Void>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Void>
     * @param dto Cambio Rol Request data transfer object used to scope this Response Entity&lt;Void>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequestDTO dto,
            Authentication authentication) {
        userAdminService.changeRole(id, dto.freshRole(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── PATCH /api/v1/admin/usuarios/{id}/estado ──────────
    // F8-gerente: GERENTE limitado en service a sus creados + ACTIVO/INACTIVO.
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    /**
     * Changes Response Entity&lt;Void>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Void>
     * @param dto Cambio status user Request data transfer object used to scope this Response Entity&lt;Void>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Void>
     * @return Response Entity&lt;Void> reflecting the state after the operation
     */
    public ResponseEntity<Void> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusUserRequestDTO dto,
            Authentication authentication) {
        userAdminService.changeStatus(id, dto.freshStatus(), dto.reason(), authentication);
        return ResponseEntity.noContent().build();
    }

    // ── POST /api/v1/admin/usuarios ──────────
    // F8-gerente: GERENTE crea solo LECTOR/BIBLIOTECARIO (service lo verifica).
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    /**
     * Creates Response Entity&lt;Usuario Response DTO>.
     *
     * @param dto Crear user Admin Request data transfer object used to scope this Response Entity&lt;Usuario Response DTO>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Usuario Response DTO>
     * @return Response Entity&lt;Usuario Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserAdminRequestDTO dto, Authentication authentication) {
        UserResponseDTO created = userAdminService.createUser(dto, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── DELETE /api/v1/admin/usuarios/{id} soft INACTIVO ──────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(name = "motivo", required = false) String reason, Authentication authentication) {
        userAdminService.deleteUser(id, reason, authentication);
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/v1/admin/usuarios/{id}/historial-motivos ──────────
    // V50/OBS-28: historial de motivos de cambio de estado/eliminación,
    // más reciente primero.
    @GetMapping("/{id}/historial-motivos")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<java.util.List<com.uteq.backend.dto.UserReasonChangeResponseDTO>> historyReasons(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.historyReasons(id));
    }
}
