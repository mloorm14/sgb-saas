package com.uteq.backend.entity;

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
 * que {@link Prestamo}) para mantener el repositorio CRUD libre de joins.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "reservaciones")
public class Reservacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "libro_id", nullable = false)
    private Long libroId;

    @Column(name = "estado_reservacion_id", nullable = false)
    private Integer estadoReservacionId;

    @Column(name = "fecha_reserva", nullable = false)
    private OffsetDateTime fechaReserva;

    @Column(name = "fecha_limite_retiro", nullable = false)
    private OffsetDateTime fechaLimiteRetiro;
}
