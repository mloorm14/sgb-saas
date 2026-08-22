package com.uteq.backend.controller;

import com.uteq.backend.dto.DevolucionCompletaResponseDTO;
import com.uteq.backend.dto.DevolucionHistorialDTO;
import com.uteq.backend.dto.DevolucionRequestDTO;
import com.uteq.backend.dto.TipoDanoDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.service.DevolucionService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devoluciones")
public class DevolucionController {

    private final DevolucionService devolucionService;
    private final UsuarioRepository usuarioRepo;

    public DevolucionController(DevolucionService devolucionService,
                                UsuarioRepository usuarioRepo) {
        this.devolucionService = devolucionService;
        this.usuarioRepo = usuarioRepo;
    }

    @PostMapping("/prestamo/{prestamoId}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<DevolucionCompletaResponseDTO> registrarDevolucion(
            @PathVariable Long prestamoId,
            @Valid @RequestBody DevolucionRequestDTO dto,
            Authentication authentication) {
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(devolucionService.registrarDevolucion(prestamoId, dto, bibliotecarioId));
    }

    @GetMapping("/historial")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<List<DevolucionHistorialDTO>> historialDevoluciones(
            Authentication authentication) {
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());
        return ResponseEntity.ok(devolucionService.historialDevoluciones(bibliotecarioId));
    }

    @GetMapping("/tipos-dano")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<List<TipoDanoDTO>> listarTiposDano() {
        return ResponseEntity.ok(devolucionService.listarTiposDano());
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + correo));
        return usuario.getId();
    }
}
