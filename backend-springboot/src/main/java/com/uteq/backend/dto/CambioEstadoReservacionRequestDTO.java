package com.uteq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Mismo criterio que CambioEstadoSugerenciaRequestDTO / CambioEstadoUsuarioRequestDTO:
// el body solo lleva el estado destino. El patrón restringe a las dos acciones
// manuales que el staff puede hacer sobre una reservación pendiente (aceptar ->
// LISTA_PARA_RETIRO, rechazar -> CANCELADA); RETIRADA queda reservada para cuando
// exista el flujo de entrega del libro y EXPIRADA la aplica el SP
// sp_expirar_reservaciones_vencidas, no un humano.
public record CambioEstadoReservacionRequestDTO(

        @NotBlank(message = "El nuevo estado es obligatorio")
        @Pattern(regexp = "LISTA_PARA_RETIRO|CANCELADA",
                message = "El estado debe ser LISTA_PARA_RETIRO o CANCELADA")
        String nuevoEstado
) {}
