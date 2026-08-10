package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// Mapea la tabla "favoritos" (Módulo 9.2 del roadmap), PK compuesta
// (usuario_id, libro_id) -- un usuario no puede marcar el mismo libro dos
// veces, la propia tabla ya lo garantiza a nivel de constraint. FKs planos
// (Long), sin relaciones @ManyToOne, mismo criterio que Prestamo/Multa
// para no meter joins en el repositorio.
@Data
@NoArgsConstructor
@Entity
@Table(name = "favoritos")
@IdClass(FavoritoId.class)
public class Favorito {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Id
    @Column(name = "libro_id")
    private Long libroId;

    @Column(name = "agregado_en", updatable = false)
    private OffsetDateTime agregadoEn;

    public Favorito(Long usuarioId, Long libroId) {
        this.usuarioId = usuarioId;
        this.libroId = libroId;
    }

    // agregado_en tiene DEFAULT NOW() a nivel de columna, pero Hibernate
    // igual envía la columna en el INSERT (con null si no se fija acá),
    // lo que violaría el NOT NULL -- mismo motivo por el que Libro fija
    // fechaRegistro en @PrePersist en vez de confiar en el default SQL.
    @PrePersist
    private void antesDeGuardar() {
        this.agregadoEn = OffsetDateTime.now();
    }
}
