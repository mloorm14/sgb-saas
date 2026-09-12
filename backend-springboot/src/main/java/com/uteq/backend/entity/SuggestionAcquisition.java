package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

// Sugerencias de adquisición (estado VARCHAR con CHECK, FKs planos sin relaciones JPA).
@Data
@NoArgsConstructor
@Entity
@Table(name = "sugerencias_adquisicion")
public class SuggestionAcquisition {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "titulo", nullable = false, length = 255)
    private String title;

    @JsonProperty("autor")
    @Column(name = "autor", length = 150)  private String author;

    @Column(length = 13)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String justificacion;

    @Column(name = "estado", nullable = false, length = 20)
    @JsonProperty("estado")
    private String status = PENDIENTE;

    @Column(name = "revisado_por")
    @JsonProperty("revisadoPor")
    private Long revisadoBy;

    @JsonProperty("proveedorId")
        @Column(name = "proveedor_id")  private Integer supplierId;

    @Column(name = "creado_en", updatable = false)  private OffsetDateTime created;

    @PrePersist
    private void antesGuardar() {
        this.created = OffsetDateTime.now();
        if (this.status == null) {
            this.status = PENDIENTE;
        }
    }
}
