package com.uteq.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

// Mapea la tabla "categorias", ya existente en db/schema.sql (Módulo 9.1
// del roadmap: tabla huérfana sin entidad detrás hasta esta rama). Mismo
// patrón simple que Editorial.java: catálogo plano, id SERIAL, sin campos
// adicionales.
@Data
@NoArgsConstructor
@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;
}
