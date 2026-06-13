package com.uteq.backend;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "libros")
public class Libro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String titulo;

    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Size(max = 300)
    @Column(length = 300)
    private String autor;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editorial_id")
    private Editorial editorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idioma_id")
    private Idioma idioma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id")
    private EstadoLibro estado;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PrePersist
    private void antesDeGuardar() {
        this.creadoEn = OffsetDateTime.now();
        this.actualizadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    private void antesDeCualquierUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}