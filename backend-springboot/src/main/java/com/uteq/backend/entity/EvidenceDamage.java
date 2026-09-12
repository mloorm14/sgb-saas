package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "evidencia_dano")
public class EvidenceDamage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("registroDanoId")
    @Column(name = "registro_dano_id", nullable = false)  private Long registrationDamageId;

    @Column(name = "archivo_nombre", nullable = false, length = 255)
    private String fileName;

    @JsonProperty("archivoTipo")
    @Column(name = "archivo_tipo", nullable = false, length = 100)  private String fileType;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "archivo_bytes", nullable = false, columnDefinition = "BYTEA")
    private byte[] fileBytes;

    @Column(name = "subido_en", nullable = false)
    @JsonProperty("subidoEn")
    private OffsetDateTime subido;
}
