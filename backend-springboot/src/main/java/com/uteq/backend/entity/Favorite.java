package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// Favoritos del lector (PK compuesta usuario+libro, FKs planos sin joins).
@Data
@NoArgsConstructor
@Entity
@Table(name = "favoritos")
@IdClass(FavoriteId.class)
public class Favorite {

    @Id
    @JsonProperty("usuarioId")
        @Column(name = "usuario_id")  private Long userId;

    @Id
    @Column(name = "libro_id")
    private Long bookId;

    @JsonProperty("agregadoEn")
    @Column(name = "agregado_en", updatable = false)  private OffsetDateTime agregado;

    public Favorite(Long userId, Long bookId) {
        this.userId = userId;
        this.bookId = bookId;
    }

    // agregado_en tiene DEFAULT NOW() a nivel de columna, pero Hibernate
    // igual envía la columna en el INSERT (con null si no se fija acá),
    // lo que violaría el NOT NULL -- mismo motivo por el que Libro fija
    // fechaRegistro en @PrePersist en vez de confiar en el default SQL.
    @PrePersist
    private void antesGuardar() {
        this.agregado = OffsetDateTime.now();
    }
}
