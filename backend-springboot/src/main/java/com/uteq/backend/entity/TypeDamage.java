package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tipos_dano")
public class TypeDamage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonProperty("nombre")
    @Column(name = "nombre", nullable = false, unique = true, length = 50)  private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoryDamage category;

    @Column(name = "tipo_costo", nullable = false, length = 10)
    private String typeCost; // FIJO | PORCENTAJE

    @JsonProperty("valor")
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)  private BigDecimal value;

    @Column(name = "activo", nullable = false)
    private Boolean active = true;

    // Compatibilidad para tests viejos que usan precio
    @Transient
    /**
     * Retrieves precio.
     *
     * @return big decimal with the resulting state after the operation
     */
    public BigDecimal getPrice() { return this.value; }
    /**
         * setPrice.
     * @return resultado de la operacion
     */
    public void setPrice(BigDecimal price) { this.value = price; if (this.typeCost==null) this.typeCost="FIJO"; }
}
