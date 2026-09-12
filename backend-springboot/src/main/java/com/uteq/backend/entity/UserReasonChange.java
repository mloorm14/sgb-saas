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
 * Mapea la tabla {@code usuario_motivos_cambio} 1:1, sin lógica de negocio ni
 * relaciones JPA (mismo criterio que {@link Loan}/{@link AuditLogAudit}:
 * columnas planas, sin joins). Guarda el {@code motivo} de un cambio de
 * estado o eliminación de usuario -- dato que
 * {@link com.uteq.backend.service.UserAdminService} recibe como
 * parámetro suelto y que {@code trg_auditoria_usuarios} no puede reconstruir
 * porque no es columna de {@code usuarios} (ver V50 y OBS-28). Complementa a
 * {@link AuditLogAudit}, no la reemplaza.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario_motivos_cambio")
public class UserReasonChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    // 'CAMBIO_ESTADO' o 'ELIMINACION' -- restringido en el motor por un
    // CHECK (V50); este mapeo no lo repite en Java.
    @Column(name = "tipo_cambio", nullable = false, length = 20)  private String typeChange;

    @Column(name = "estado_anterior")
    private Integer statusAnterior;

    @Column(name = "estado_nuevo")
    @JsonProperty("estadoNuevo")
    private Integer statusFresh;

    @JsonProperty("motivo")
    @Column(name = "motivo", columnDefinition = "TEXT")  private String reason;

    @Column(name = "ejecutado_por", nullable = false)  private Long executedBy;

    @Column(name = "creado_en", nullable = false)  private OffsetDateTime created;
}
