package com.uteq.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

// Mapea la tabla "autores" (Módulo 9.1 del roadmap). BIGSERIAL en el
// schema (a diferencia de categorias, que es SERIAL) porque autores.id ya
// se definió como BIGINT en db/schema.sql -- de ahí el Long en vez de
// Integer, igual criterio que Libro.id.
@Data
@NoArgsConstructor
@Entity
@Table(name = "autores")
public class Autor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nombre;
}
