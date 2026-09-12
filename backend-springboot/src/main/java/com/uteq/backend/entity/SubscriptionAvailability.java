package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "suscripciones_disponibilidad", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id","libro_id"}))
public class SubscriptionAvailability {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "libro_id", nullable = false)
    private Long bookId;

    @JsonProperty("creadoEn")
    @Column(name = "creado_en", nullable = false)  private OffsetDateTime created;
}
