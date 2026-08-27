package com.uteq.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tipos_dano")
public class TipoDano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaDano categoria;

    @Column(name = "tipo_costo", nullable = false, length = 10)
    private String tipoCosto; // FIJO | PORCENTAJE

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Boolean activo = true;

    // Compatibilidad para tests viejos que usan precio
    @Transient
    public BigDecimal getPrecio() { return this.valor; }
    public void setPrecio(BigDecimal precio) { this.valor = precio; if (this.tipoCosto==null) this.tipoCosto="FIJO"; }
}
