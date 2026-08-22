package com.uteq.backend.entity;

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
public class EvidenciaDano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registro_dano_id", nullable = false)
    private Long registroDanoId;

    @Column(name = "archivo_nombre", nullable = false, length = 255)
    private String archivoNombre;

    @Column(name = "archivo_tipo", nullable = false, length = 100)
    private String archivoTipo;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "archivo_bytes", nullable = false, columnDefinition = "BYTEA")
    private byte[] archivoBytes;

    @Column(name = "subido_en", nullable = false)
    private OffsetDateTime subidoEn;
}
