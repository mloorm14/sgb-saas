package com.uteq.backend.controller;

import com.uteq.backend.dto.EvidenciaDanoResponseDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.service.DevolucionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devoluciones")
public class EvidenciaDanoController {

    private final DevolucionService devolucionService;
    private final UsuarioRepository usuarioRepo;

    public EvidenciaDanoController(DevolucionService devolucionService,
                                    UsuarioRepository usuarioRepo) {
        this.devolucionService = devolucionService;
        this.usuarioRepo = usuarioRepo;
    }

    @PostMapping(value = "/evidencia/{registroDanoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<EvidenciaDanoResponseDTO> subirEvidencia(
            @PathVariable Long registroDanoId,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {
        Long bibliotecarioId = resolverIdPorCorreo(authentication.getName());
        return ResponseEntity.ok(devolucionService.subirEvidencia(registroDanoId, archivo, bibliotecarioId));
    }

    @GetMapping("/evidencia/{registroDanoId}")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<List<EvidenciaDanoResponseDTO>> listarEvidencias(
            @PathVariable Long registroDanoId) {
        return ResponseEntity.ok(devolucionService.listarEvidencias(registroDanoId));
    }

    @GetMapping("/evidencia/{id}/archivo")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<byte[]> obtenerArchivo(@PathVariable Long id) {
        var evidencia = devolucionService.obtenerArchivoBinario(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidencia.archivoTipo()))
                .body(evidencia.archivoBytes());
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + correo));
        return usuario.getId();
    }
}
