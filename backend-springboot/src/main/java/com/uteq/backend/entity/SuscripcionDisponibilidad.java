package com.uteq.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "suscripciones_disponibilidad", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id","libro_id"}))
public class SuscripcionDisponibilidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "libro_id", nullable = false)
    private Long libroId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;
}
