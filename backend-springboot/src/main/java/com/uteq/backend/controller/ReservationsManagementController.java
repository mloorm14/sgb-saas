package com.uteq.backend.controller;

import com.uteq.backend.dto.HistoryReservationDTO;
import com.uteq.backend.dto.UserReservationsManagementDTO;
import com.uteq.backend.service.ReservationsManagementService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lecturas de la ventanilla de reservaciones del bibliotecario (módulo
 * "Reservaciones" del sidebar): encontrar al usuario por correo y armar
 * la pantalla -- tarjeta de identificación + historial de reservaciones.
 *
 * La CREACIÓN de la reservación se mantiene en POST /api/v1/reservaciones
 * (ReservacionController.crear), que ahora acepta fechaRetiro opcional.
 * Las sugerencias de usuarios se reutilizan de
 * GET /api/v1/prestamos/gestion/sugerencias-usuarios (mismo endpoint).
 */
@RestController
@RequestMapping("/api/v1/reservaciones/gestion")
@Validated
public class ReservationsManagementController {

    private final ReservationsManagementService reservationsManagementService;

    public ReservationsManagementController(
            ReservationsManagementService reservationsManagementService) {
        this.reservationsManagementService = reservationsManagementService;
    }

    // ── GET /api/v1/reservaciones/gestion/buscar-usuario?correo= ──
    // Busca el usuario por correo completo y retorna su tarjeta de
    // identificación + cantidad de reservas activas.
    @GetMapping("/buscar-usuario")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<UserReservationsManagementDTO> searchUser(
            @RequestParam("correo") @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$",
                     message = "Ingresa un correo electrónico válido")
            String email) {
        return ResponseEntity.ok(reservationsManagementService.searchByEmail(email));
    }

    // ── GET /api/v1/reservaciones/gestion/historial-reservaciones?usuarioId= ──
    // Historial de reservaciones del usuario con título del libro resuelto.
    @GetMapping("/historial-reservaciones")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Handles history reservations.
     *
     * @param userId numeric identifier used to scope this history reservations
     * @return Response Entity&lt;List<Historial reservation DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<HistoryReservationDTO>> historyReservations(
            @RequestParam("usuarioId") Long userId) {
        return ResponseEntity.ok(
                reservationsManagementService.historyReservations(userId));
    }
}
