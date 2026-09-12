package com.uteq.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

// estadoReservacionId y fechaReserva NO viajan en el body: el service los
// calcula (estado inicial PENDIENTE, fechaReserva = now()).
// usuarioId sí viaja en el body porque BIBLIOTECARIO/GERENTE pueden
// reservar en nombre de otro usuario; si el Authentication es LECTOR,
// el service exige que coincida con su propio id.
// fechaRetiro es opcional: si se provee, se usa como base para
// fechaLimiteRetiro; si no, se calcula now() + días de gracia.
public record ReservationRequestDTO(

        @NotNull(message = "El usuario es obligatorio") @JsonProperty("usuarioId") Long userId,

        @NotNull(message = "El libro es obligatorio") @JsonProperty("libroId") Long bookId,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonSetter(nulls = Nulls.AS_EMPTY) @JsonProperty("fechaRetiro") OffsetDateTime datePickup
) {}
