package com.uteq.backend.controller;

import com.uteq.backend.dto.ChangeStatusReservationRequestDTO;
import com.uteq.backend.dto.ReservationTodayResponseDTO;
import com.uteq.backend.dto.ReservationRequestDTO;
import com.uteq.backend.dto.ReservationResponseDTO;
import com.uteq.backend.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservaciones")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // ── POST /api/v1/reservaciones ────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<ReservationResponseDTO> create(
            @Valid @RequestBody ReservationRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(dto, authentication));
    }

    // ── GET /api/v1/reservaciones/hoy ──────────────────────
    // Dashboard del bibliotecario: reservaciones que vencen hoy, sin
    // paginar (volumen bajo por diseño -- es "las de hoy", no el histórico).
    @GetMapping("/hoy")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Handles reservations de hoy.
     *
     * @return response entity<list<reservacion hoy response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<ReservationTodayResponseDTO>> reservationsToday() {
        return ResponseEntity.ok(reservationService.searchReservationsToday());
    }

    @GetMapping("/proximas")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Handles reservations proximas.
     *
     * @return response entity<list<reservacion hoy response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<ReservationTodayResponseDTO>> reservationsNexts() {
        return ResponseEntity.ok(reservationService.searchReservationsNexts());
    }

    // ── PATCH /api/v1/reservaciones/{id}/estado ────────────
    // El staff acepta (PENDIENTE -> LISTA_PARA_RETIRO) o rechaza
    // (PENDIENTE -> CANCELADA) la reservación de un lector. Es la acción
    // manual que faltaba del RF-10: hasta ahora el LECTOR podía crear y el
    // sistema expirar, pero nadie podía marcar "listo para retirar".
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Actualiza change status con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<ReservationResponseDTO> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusReservationRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(reservationService.changeStatus(id, dto, authentication));
    }

    // ── GET /api/v1/reservaciones/usuario/{usuarioId} ─────
    /**
     * Consulta list by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<Page<ReservationResponseDTO>> listByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "dateReservation") Pageable pageable) {
        return ResponseEntity.ok(
                reservationService.listByUser(userId, authentication, pageable));
    }
}