package com.uteq.backend.controller;

import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.SubscriptionAvailabilityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/libros")
public class SubscriptionAvailabilityController {

    private final SubscriptionAvailabilityService service;
    private final UserRepository userRepo;

    public SubscriptionAvailabilityController(SubscriptionAvailabilityService service, UserRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    @PostMapping("/{libroId}/suscripciones")
    @PreAuthorize("isAuthenticated()")
    /**
     * Procesa suscribir y devuelve el resultado calculado por el backend.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param auth identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> suscribir(@PathVariable("libroId") Long bookId, Authentication auth) {
        Long userId = resolveUserId(auth);
        service.suscribir(userId, bookId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{libroId}/suscripciones")
    @PreAuthorize("isAuthenticated()")
    /**
     * Procesa desuscribir y devuelve el resultado calculado por el backend.
     *
     * @param bookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param auth identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<Void> desuscribir(@PathVariable("libroId") Long bookId, Authentication auth) {
        Long userId = resolveUserId(auth);
        service.desuscribir(userId, bookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suscripciones/mias")
    @PreAuthorize("isAuthenticated()")
    /**
     * Procesa mis subscriptions y devuelve el resultado calculado por el backend.
     *
     * @param auth identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<List<Long>> misSubscriptions(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(service.listBooksIds(userId));
    }

    private Long resolveUserId(Authentication auth) {
        String email = auth.getName();
        try {
            return Long.parseLong(email);
        } catch (NumberFormatException e) {
            return userRepo.findByEmail(email)
                    .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + email))
                    .getId();
        }
    }
}
