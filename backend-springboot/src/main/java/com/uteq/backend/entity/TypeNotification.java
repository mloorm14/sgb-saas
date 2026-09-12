package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catálogo de tipos de notificación (tabla {@code tipos_notificacion}:
 * VENCIMIENTO, MULTA, RESERVA_CADUCADA -- sembrados en la migración
 * V6__notificaciones.sql). Mismo patrón que {@link StatusBook}/
 * {@link StatusUser}: sin lógica de negocio, solo id + nombre.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "tipos_notificacion")
public class TypeNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 30)
    @JsonProperty("nombre")
    @Column(name = "nombre", nullable = false, unique = true, length = 30)  private String name;
}
