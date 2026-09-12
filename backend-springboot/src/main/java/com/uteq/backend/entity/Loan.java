package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mapea la tabla {@code prestamos} 1:1, sin lógica de negocio ni relaciones
 * JPA a otras entidades: las columnas de llave foránea se exponen como
 * identificadores planos (Long/Integer) a propósito, para que
 * {@link com.uteq.backend.repository.LoanRepository} pueda ofrecer
 * únicamente consultas derivadas triviales (sin joins). Las operaciones que
 * requieren validación cruzada o transacciones atómicas complejas viven en
 * los procedimientos de db/procs/ (ver
 * {@link com.uteq.backend.repository.LoanProcedureRepository}).
 */
@NamedStoredProcedureQuery(
        name = "Prestamo.registrarDevolucion",
        procedureName = "sp_registrar_devolucion",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.IN, name = "p_prestamo_id", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_prestamo_id", type = Long.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_hubo_multa", type = Boolean.class),
                @StoredProcedureParameter(mode = ParameterMode.OUT, name = "o_monto_multa", type = BigDecimal.class)
        }
)
@Data
@NoArgsConstructor
@Entity
@Table(name = "prestamos")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "libro_id", nullable = false)
    private Long bookId;

    @JsonProperty("bibliotecarioId")
    @Column(name = "bibliotecario_id", nullable = false)  private Long librarianId;

    @Column(name = "reservacion_id")
    private Long reservationId;

    @JsonProperty("fechaPrestamo")
    @Column(name = "fecha_prestamo", nullable = false)  private OffsetDateTime dateLoan;

    @Column(name = "fecha_devolucion_estimada", nullable = false)
    private OffsetDateTime dateLoanReturnEstimada;

    @Column(name = "fecha_devolucion_real")
    @JsonProperty("fechaDevolucionReal")
    private OffsetDateTime dateLoanReturnReal;

    @Column(name = "renovaciones_realizadas", nullable = false, columnDefinition = "SMALLINT")
    private Short renewalsRealizadas = (short) 0;

    @JsonProperty("estadoPrestamoId")
    @Column(name = "estado_prestamo_id", nullable = false)  private Integer statusLoanId;
}
