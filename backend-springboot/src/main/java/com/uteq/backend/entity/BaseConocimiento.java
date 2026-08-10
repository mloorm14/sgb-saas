package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mapea la tabla {@code base_conocimiento} (migración V9, Módulo H): pares
 * pregunta/respuesta curados por el equipo (HORARIOS, POLITICAS, MULTAS) que
 * ChatbotService inyecta al prompt de sistema de Gemini como grounding -- el
 * modelo solo responde con este contexto real, nunca inventa datos. Sin
 * lógica de negocio, mismo criterio que el resto de entidades del repo.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "base_conocimiento")
public class BaseConocimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "categoria", nullable = false, length = 40)
    private String categoria;

    @Column(name = "pregunta_ejemplo", nullable = false, columnDefinition = "TEXT")
    private String preguntaEjemplo;

    @Column(name = "respuesta", nullable = false, columnDefinition = "TEXT")
    private String respuesta;

    @Column(name = "activo", nullable = false)
    private Boolean activo;
}
