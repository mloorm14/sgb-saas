package com.uteq.backend.controller;

import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.service.SuscripcionDisponibilidadService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/libros")
public class SuscripcionDisponibilidadController {

    private final SuscripcionDisponibilidadService service;
    private final UsuarioRepository usuarioRepo;

    public SuscripcionDisponibilidadController(SuscripcionDisponibilidadService service, UsuarioRepository usuarioRepo) {
        this.service = service;
        this.usuarioRepo = usuarioRepo;
    }

    @PostMapping("/{libroId}/suscripciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> suscribir(@PathVariable Long libroId, Authentication auth) {
        Long usuarioId = resolverUsuarioId(auth);
        service.suscribir(usuarioId, libroId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{libroId}/suscripciones")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> desuscribir(@PathVariable Long libroId, Authentication auth) {
        Long usuarioId = resolverUsuarioId(auth);
        service.desuscribir(usuarioId, libroId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suscripciones/mias")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Long>> misSuscripciones(Authentication auth) {
        Long usuarioId = resolverUsuarioId(auth);
        return ResponseEntity.ok(service.listarLibrosIds(usuarioId));
    }

    private Long resolverUsuarioId(Authentication auth) {
        String correo = auth.getName();
        try {
            return Long.parseLong(correo);
        } catch (NumberFormatException e) {
            return usuarioRepo.findByCorreo(correo)
                    .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + correo))
                    .getId();
        }
    }
}
