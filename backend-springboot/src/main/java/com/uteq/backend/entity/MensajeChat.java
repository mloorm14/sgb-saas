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
import java.util.UUID;

/**
 * Mapea la tabla {@code mensajes_chat} (migración V9, Módulo H) 1:1, sin
 * lógica de negocio. {@code rol} es {@code "USUARIO"} (lo que escribió el
 * lector) o {@code "ASISTENTE"} (la respuesta de Gemini), constraint CHECK
 * en la BD (migración V9). {@code sesionId} se guarda como identificador
 * plano, sin {@code @ManyToOne}, mismo criterio que el resto de entidades
 * del repo.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "mensajes_chat")
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(name = "rol", nullable = false, length = 10)
    private String rol;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;
}
