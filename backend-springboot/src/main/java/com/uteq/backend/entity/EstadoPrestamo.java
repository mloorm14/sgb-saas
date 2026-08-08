package com.uteq.backend.entity;

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
 * Mapea el catálogo {@code estados_prestamo} (ACTIVO, RENOVADO, DEVUELTO,
 * VENCIDO -- ver db/seed.sql), que hasta ahora no tenía entidad JPA propia:
 * {@link Prestamo#getEstadoPrestamoId()} se usaba siempre como Integer
 * plano. Se agrega para poder resolver el nombre del estado por su id (y
 * viceversa) al validar una renovación, mismo patrón que
 * {@link EstadoReservacion}.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "estados_prestamo")
public class EstadoPrestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String nombre;
}
