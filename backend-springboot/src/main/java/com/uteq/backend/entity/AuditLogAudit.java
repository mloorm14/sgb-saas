package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * (mismo criterio que {@link com.uteq.backend.entity.Loan}: columnas
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
public class AuditLogAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
        @Column(name = "usuario_id")  private Long userId;

    @Column(name = "tipo_operacion", nullable = false, length = 20)
    private String typeOperacion;

    @JsonProperty("tablaAfectada")
    @Column(name = "tabla_afectada", nullable = false, length = 50)  private String tableAfectada;

    @Column(name = "registro_id")
    private Long registrationId;

    @Column(name = "detalles", nullable = false)
    private String detalles;

    @Column(name = "ip_origen", length = 45)
    @JsonProperty("ipOrigen")
    private String ipSource;

    @Column(name = "fecha_hora", nullable = false)
    @JsonProperty("fechaHora")
    private OffsetDateTime dateTime;
}
