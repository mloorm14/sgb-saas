package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@Entity
@Table(name = "libros")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 13)
    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @NotBlank
    @Size(max = 255)
    @JsonProperty("titulo")
    @Column(name = "titulo", nullable = false, length = 255)  private String title;

    @Column(name = "resumen", columnDefinition = "TEXT")
    private String summary;

    @Size(max = 1000)
    @JsonProperty("portadaUrl")
    @Column(name = "portada_url", length = 1000)  private String coverUrl;

    // Portada como imagen binaria en BD (V13__portada_imagen.sql). LAZY a
    // proposito: es un binario potencialmente pesado y el listado de
    // /api/v1/libros pagina resultados -- este campo NUNCA debe viajar en
    // el SELECT por defecto salvo que se pida explicito
    // (LibroService.obtenerPortada, GET /api/v1/libros/{id}/portada).
    // Nota: el lazy real de atributos basicos exige bytecode enhancement
    // de Hibernate (fuera de alcance de esta rama); sin el, Hibernate
    // carga el binario igual en el SELECT, pero el contrato de API se
    // mantiene: LibroResponseDTO expone solo metadata (tienePortada/
    // portadaNombre/portadaTipo), nunca el byte[].
    // SIN @Lob a proposito: en PostgreSQL byte[] nativo ya mapea a bytea;
    // @Lob lo fuerza a OID/large-object y el driver envia el parametro con
    // un tipo incompatible ("column is of type bytea but expression is of
    // type bigint"), verificado en LibroPortadaIntegrationTest real.
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "portada_imagen", columnDefinition = "BYTEA")
    private byte[] coverImage;

    @Size(max = 255)
    @JsonProperty("portadaNombre")
    @Column(name = "portada_nombre", length = 255)  private String coverName;

    @Size(max = 100)
    @Column(name = "portada_tipo", length = 100)  private String coverType;

    @Column(name = "portada_tamanio")  private Integer coverTamanio;

    @Column(name = "anio_publicacion", nullable = false, columnDefinition = "SMALLINT")
    private Short yearPublication;

    @JsonProperty("numeroPaginas")
    @Column(name = "numero_paginas", columnDefinition = "SMALLINT")  private Short numberPages;

    @Column(name = "precio_base", precision = 10, scale = 2)
    private java.math.BigDecimal priceBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonProperty("editorial")
    @JoinColumn(name = "editorial_id", nullable = false)  private Publisher publisher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idioma_id", nullable = false)
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    @JsonProperty("estado")
    private StatusBook status;

    // Proveedor opcional (S/P si null) — FK nullable, ON DELETE SET NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonProperty("proveedor")
    @JoinColumn(name = "proveedor_id")  private Supplier supplier;

    @Column(name = "stock_total", nullable = false, columnDefinition = "SMALLINT")
    private Short stockTotal = (short) 1;

    @Column(name = "stock_disponible", nullable = false, columnDefinition = "SMALLINT")
    private Short stockAvailable = (short) 1;

    @Size(max = 50)
    @Column(name = "ubicacion_fisica", length = 50)
    @JsonProperty("ubicacionFisica")
    private String locationPhysical;

    @JsonProperty("fechaRegistro")
    @Column(name = "fecha_registro", updatable = false)  private OffsetDateTime dateRegistration;

    @Column(name = "actualizado_en")  private OffsetDateTime updated;

    // Categorías/autores (@ManyToMany LAZY): inicializar dentro de la transacción o lanza LazyInitializationException.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "libro_categorias",
            joinColumns = @JoinColumn(name = "libro_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "libro_autores",
            joinColumns = @JoinColumn(name = "libro_id"),
            inverseJoinColumns = @JoinColumn(name = "autor_id")
    )
    private Set<Author> authors = new HashSet<>();

    @PrePersist
    private void antesGuardar() {
        this.dateRegistration = OffsetDateTime.now();
        this.updated = OffsetDateTime.now();
    }

    @PreUpdate
    private void antesCualquierUpdate() {
        this.updated = OffsetDateTime.now();
    }
}
