package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Mapea la tabla {@code reservaciones} 1:1, sin lógica de negocio. Las
 * llaves foráneas se exponen como identificadores planos (mismo criterio
 * que {@link Loan}) para mantener el repositorio CRUD libre de joins.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "reservaciones")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "libro_id", nullable = false)
    private Long bookId;

    @JsonProperty("estadoReservacionId")
    @Column(name = "estado_reservacion_id", nullable = false)  private Integer statusReservationId;

    @Column(name = "fecha_reserva", nullable = false)
    private OffsetDateTime dateReservation;

    @JsonProperty("fechaLimiteRetiro")
    @Column(name = "fecha_limite_retiro", nullable = false)  private OffsetDateTime dateLimitPickup;
}
