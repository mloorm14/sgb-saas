package com.uteq.backend.dto;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Tarjeta de identificación del usuario para la ventanilla de reservaciones
 * (GET /api/v1/reservaciones/gestion/buscar-usuario?correo=).
 *
 * Similar a UsuarioPrestamosGestionDTO pero enfocado en reservaciones:
 * - cantidadReservasActivas: count de reservas en estado PENDIENTE o LISTA_PARA_RETIRO
 * - limiteReservas: configuracion_sistema ('limite_reservas_por_usuario')
 */
public record UserReservationsManagementDTO(
        Long id, @JsonProperty("nombreCompleto") String nameFull, @JsonProperty("correo") String email, @JsonProperty("estadoCuenta") String statusAccount, @JsonProperty("cantidadReservasActivas") long quantityReservationsActives, @JsonProperty("limiteReservas") int limitReservations
) {}
