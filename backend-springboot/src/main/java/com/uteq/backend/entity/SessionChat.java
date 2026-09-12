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
import java.util.UUID;

/**
 * Sesiones de chat del lector (1:1, sin relaciones JPA).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sesiones_chat")
public class SessionChat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime created;

    @JsonProperty("ultimaActividad")
    @Column(name = "ultima_actividad", nullable = false)  private OffsetDateTime lastActividad;
}
