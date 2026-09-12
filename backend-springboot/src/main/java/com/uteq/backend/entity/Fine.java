package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedStoredProcedureQueries;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mapea la tabla {@code multas} 1:1, sin lógica de negocio. {@code prestamoId}
 * se expone como identificador plano (mismo criterio que {@link Loan})
 * para mantener el repositorio CRUD libre de joins.
 */
@NamedStoredProcedureQueries({
        @NamedStoredProcedureQuery(
                name = "Multa.pagarMulta",
                procedureName = "sp_pagar_multa",
                parameters = {
                        @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_multa_id", type = Long.class),
                        @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_multa_id", type = Long.class),
                        @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_usuario_desbloqueado", type = Boolean.class)
                }
        ),
        @NamedStoredProcedureQuery(
                name = "Multa.anularMulta",
                procedureName = "sp_anular_multa",
                parameters = {
                        @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_multa_id", type = Long.class),
                        @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_motivo", type = String.class),
                        @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_rol_ejecutor", type = String.class),
                        @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_multa_id", type = Long.class),
                        @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_usuario_desbloqueado", type = Boolean.class)
                }
        )
})
@Data
@NoArgsConstructor
@Entity
@Table(name = "multas")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sin unique=true: un préstamo puede generar más de una multa (ej. daño
    // + atraso por separado) -- ver V3__multas_multiples_por_prestamo.sql.
    @JsonProperty("prestamoId")
    @Column(name = "prestamo_id", nullable = false)  private Long loanId;

    @Column(name = "monto", nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @JsonProperty("montoPagado")
    @Column(name = "monto_pagado", nullable = false, precision = 8, scale = 2)  private BigDecimal amountPaid;

    @Column(name = "estado_multa_id", nullable = false)
    private Integer statusFineId;

    @JsonProperty("fechaGenerada")
    @Column(name = "fecha_generada", nullable = false)  private OffsetDateTime dateGenerated;

    @Column(name = "fecha_pagada")
    private OffsetDateTime datePaid;

    @Column(name = "observaciones", length = 255)
    @JsonProperty("observaciones")
    private String observations;
}
