package com.uteq.backend.controller;

import com.uteq.backend.service.CredentialQrService;
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
public class CredentialQrController {

    private final CredentialQrService service;

    public CredentialQrController(CredentialQrService service) {
        this.service = service;
    }

    @GetMapping(value = "/mi-credencial", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Procesa mi credential y devuelve el resultado calculado por el backend.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<byte[]> miCredential(Authentication authentication) {
        byte[] image = service.generateImageQrOwn(authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"credencial-qr.png\"")
                .body(image);
    }
}
