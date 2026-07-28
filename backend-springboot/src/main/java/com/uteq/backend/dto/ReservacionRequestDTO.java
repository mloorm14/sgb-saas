package com.uteq.backend.dto;

import jakarta.validation.constraints.NotNull;

// estadoReservacionId, fechaReserva y fechaLimiteRetiro NO viajan en el
// body: el service los calcula (estado inicial PENDIENTE, fechaReserva =
// now(), fechaLimiteRetiro = now() + días de gracia). usuarioId sí viaja
// en el body porque BIBLIOTECARIO/GERENTE pueden reservar en nombre de
// otro usuario; si el Authentication es LECTOR, el service exige que
// coincida con su propio id.
public record ReservacionRequestDTO(

        @NotNull(message = "El usuario es obligatorio")
        Long usuarioId,

        @NotNull(message = "El libro es obligatorio")
        Long libroId
) {}