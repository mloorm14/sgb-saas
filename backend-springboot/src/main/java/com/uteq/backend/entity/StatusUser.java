package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo de estados de usuario (tabla {@code estados_usuario}: ACTIVO,
 * BLOQUEADO_POR_MULTA, INACTIVO, PENDIENTE_VERIFICACION). Mismo patrón que
 * {@link StatusBook}: sin lógica de negocio, solo id + nombre.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "estados_usuario")
public class StatusUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 30)
    @JsonProperty("nombre")
    @Column(name = "nombre", nullable = false, unique = true, length = 30)  private String name;
}
