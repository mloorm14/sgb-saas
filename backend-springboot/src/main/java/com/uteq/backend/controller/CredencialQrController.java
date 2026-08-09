package com.uteq.backend.controller;

import com.uteq.backend.service.CredencialQrService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Modulo 8: sin parametro de usuarioId en la URL a proposito -- cada
// LECTOR ve UNICAMENTE su propia credencial, resuelta a partir del
// Authentication, para que no sea posible pedir el QR de otro usuario
// cambiando un id en la URL.
@RestController
@RequestMapping("/api/v1/credencial-qr")
public class CredencialQrController {

    private final CredencialQrService service;

    public CredencialQrController(CredencialQrService service) {
        this.service = service;
    }

    @GetMapping(value = "/mi-credencial", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<byte[]> miCredencial(Authentication authentication) {
        byte[] imagen = service.generarImagenQrPropio(authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"credencial-qr.png\"")
                .body(imagen);
    }
}
