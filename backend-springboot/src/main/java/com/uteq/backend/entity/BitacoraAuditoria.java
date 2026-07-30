package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Mapea la tabla {@code bitacora_auditoria} 1:1, sin lógica de negocio
 * (mismo criterio que {@link com.uteq.backend.entity.Prestamo}: columnas
 * planas, sin joins). {@code tipoOperacion} está restringido en el motor
 * por un CHECK ('INSERT','UPDATE','DELETE','LOGIN_OK','LOGIN_FAIL','LOGOUT')
 * -- este mapeo no lo repite en Java, un valor fuera de ese conjunto falla
 * al hacer INSERT, no antes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bitacora_auditoria")
public class BitacoraAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "tipo_operacion", nullable = false, length = 20)
    private String tipoOperacion;

    @Column(name = "tabla_afectada", nullable = false, length = 50)
    private String tablaAfectada;

    @Column(name = "registro_id")
    private Long registroId;

    @Column(name = "detalles", nullable = false)
    private String detalles;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;
}
