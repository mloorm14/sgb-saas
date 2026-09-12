package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RegistrationDamageDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("registroDanoId")
    @Column(name = "registro_dano_id", nullable = false)  private Long registrationDamageId;

    @Column(name = "tipo_dano_id")
    private Integer typeDamageId;

    @JsonProperty("nombreCustom")
    @Column(name = "nombre_custom", length = 100)  private String nameCustom;

    @Column(name = "precio_cobrado", nullable = false, precision = 8, scale = 2)
    private BigDecimal priceCobrado;
}
