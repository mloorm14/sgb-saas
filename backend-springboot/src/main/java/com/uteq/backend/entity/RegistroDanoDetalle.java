package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "registro_dano_detalle")
public class RegistroDanoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registro_dano_id", nullable = false)
    private Long registroDanoId;

    @Column(name = "tipo_dano_id")
    private Integer tipoDanoId;

    @Column(name = "nombre_custom", length = 100)
    private String nombreCustom;

    @Column(name = "precio_cobrado", nullable = false, precision = 8, scale = 2)
    private BigDecimal precioCobrado;
}
