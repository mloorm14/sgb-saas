package com.uteq.backend.dto;

/**
 * Tarjeta de identificación del usuario para la ventanilla de reservaciones
 * (GET /api/v1/reservaciones/gestion/buscar-usuario?correo=).
 *
 * Similar a UsuarioPrestamosGestionDTO pero enfocado en reservaciones:
 * - cantidadReservasActivas: count de reservas en estado PENDIENTE o LISTA_PARA_RETIRO
 * - limiteReservas: configuracion_sistema ('limite_reservas_por_usuario')
 */
public record UsuarioReservacionesGestionDTO(
        Long id,
        String nombreCompleto,
        String correo,
        String estadoCuenta,
        long cantidadReservasActivas,
        int limiteReservas
) {}
