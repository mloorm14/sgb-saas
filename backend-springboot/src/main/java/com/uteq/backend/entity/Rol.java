package com.uteq.backend.entity;

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
 * Catálogo de roles (tabla {@code roles}: LECTOR, BIBLIOTECARIO, GERENTE,
 * ADMIN). Mantenida simple (id + nombre) a propósito — no mapea
 * {@code descripcion} ni la tabla puente {@code rol_permisos}, que están
 * fuera del alcance de este cambio (Hibernate en modo {@code validate} no
 * exige mapear todas las columnas de una tabla).
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, unique = true, length = 30)
    private String nombre;
}
