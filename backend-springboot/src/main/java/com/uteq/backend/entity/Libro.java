package com.uteq.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@Entity
@Table(name = "libros")
public class Libro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 13)
    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String resumen;

    @Size(max = 1000)
    @Column(name = "portada_url", length = 1000)
    private String portadaUrl;

    @Column(name = "anio_publicacion", nullable = false, columnDefinition = "SMALLINT")
    private Short anioPublicacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editorial_id", nullable = false)
    private Editorial editorial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idioma_id", nullable = false)
    private Idioma idioma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoLibro estado;

    @Column(name = "stock_total", nullable = false, columnDefinition = "SMALLINT")
    private Short stockTotal = (short) 1;

    @Column(name = "stock_disponible", nullable = false, columnDefinition = "SMALLINT")
    private Short stockDisponible = (short) 1;

    @Size(max = 50)
    @Column(name = "ubicacion_fisica", length = 50)
    private String ubicacionFisica;

    @Column(name = "fecha_registro", updatable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    // Módulo 9.1 del roadmap: categorias/autores ya existían como tablas
    // huérfanas (libro_categorias/libro_autores) sin entidad ni relación
    // detrás. LAZY (default de @ManyToMany, se deja explícito por
    // consistencia con editorial/idioma/estado arriba) -- a diferencia de
    // esos tres, esta relación es una colección, no una FK simple, así que
    // toDTO()/fromDTO() en LibroService deben forzar su inicialización
    // dentro de la transacción @Transactional(readOnly = true) de
    // buscarPorId()/listar(), o lanzará LazyInitializationException fuera
    // de sesión.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "libro_categorias",
            joinColumns = @JoinColumn(name = "libro_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id")
    )
    private Set<Categoria> categorias = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "libro_autores",
            joinColumns = @JoinColumn(name = "libro_id"),
            inverseJoinColumns = @JoinColumn(name = "autor_id")
    )
    private Set<Autor> autores = new HashSet<>();

    @PrePersist
    private void antesDeGuardar() {
        this.fechaRegistro = OffsetDateTime.now();
        this.actualizadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    private void antesDeCualquierUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
