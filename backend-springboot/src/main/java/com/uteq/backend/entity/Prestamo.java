package com.uteq.backend.entity;

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
 * {@link com.uteq.backend.repository.PrestamoRepository} pueda ofrecer
 * únicamente consultas derivadas triviales (sin joins). Las operaciones que
 * requieren validación cruzada o transacciones atómicas complejas viven en
 * los procedimientos de db/procs/ (ver
 * {@link com.uteq.backend.repository.PrestamoProcedureRepository}).
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
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "libro_id", nullable = false)
    private Long libroId;

    @Column(name = "bibliotecario_id", nullable = false)
    private Long bibliotecarioId;

    @Column(name = "reservacion_id")
    private Long reservacionId;

    @Column(name = "fecha_prestamo", nullable = false)
    private OffsetDateTime fechaPrestamo;

    @Column(name = "fecha_devolucion_estimada", nullable = false)
    private OffsetDateTime fechaDevolucionEstimada;

    @Column(name = "fecha_devolucion_real")
    private OffsetDateTime fechaDevolucionReal;

    @Column(name = "renovaciones_realizadas", nullable = false, columnDefinition = "SMALLINT")
    private Short renovacionesRealizadas = (short) 0;

    @Column(name = "estado_prestamo_id", nullable = false)
    private Integer estadoPrestamoId;
}
