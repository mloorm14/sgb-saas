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
 * Pares pregunta/respuesta curados que ChatbotService inyecta como contexto a Gemini.
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
