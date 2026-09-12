package com.uteq.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/protegido")
    /**
     * Handles protegido.
     *
     * @return response entity<string> with the resulting state after the operation
     */
    public ResponseEntity<String> protegido() {
        return ResponseEntity.ok("Acceso autorizado. Estás autenticado correctamente.");
    }
}
