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
 * Mensajes de una sesión de chat (rol USUARIO/ASISTENTE, FK plano sin relaciones JPA).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "mensajes_chat")
public class MessageChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("sesionId")
    @Column(name = "sesion_id", nullable = false)  private UUID sessionId;

    @Column(name = "rol", nullable = false, length = 10)
    private String role;

    @JsonProperty("contenido")
    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")  private String content;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime created;
}
