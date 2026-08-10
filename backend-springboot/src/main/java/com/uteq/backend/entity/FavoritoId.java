package com.uteq.backend.entity;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// PK compuesta de Favorito (usuario_id + libro_id, ver db/schema.sql). Es
// la única entidad de la rama con llave compuesta -- libro_categorias y
// libro_autores no se mapean como entidad propia, sino como @ManyToMany
// en Libro (ver Libro.categorias/Libro.autores) -- por eso hace falta esta
// clase auxiliar para @IdClass, que Categoria/Autor no necesitan.
// @EqualsAndHashCode (no @Data): @IdClass exige que la clase de llave
// implemente equals()/hashCode() consistentes con los campos @Id de la
// entidad; no necesita setters ni toString de negocio.
@NoArgsConstructor
@EqualsAndHashCode
public class FavoritoId implements Serializable {

    private Long usuarioId;
    private Long libroId;

    public FavoritoId(Long usuarioId, Long libroId) {
        this.usuarioId = usuarioId;
        this.libroId = libroId;
    }
}
