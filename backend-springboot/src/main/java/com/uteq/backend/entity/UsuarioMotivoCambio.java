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
 * Mapea la tabla {@code usuario_motivos_cambio} 1:1, sin lógica de negocio ni
 * relaciones JPA (mismo criterio que {@link Prestamo}/{@link BitacoraAuditoria}:
 * columnas planas, sin joins). Guarda el {@code motivo} de un cambio de
 * estado o eliminación de usuario -- dato que
 * {@link com.uteq.backend.service.UsuarioAdminService} recibe como
 * parámetro suelto y que {@code trg_auditoria_usuarios} no puede reconstruir
 * porque no es columna de {@code usuarios} (ver V50 y OBS-28). Complementa a
 * {@link BitacoraAuditoria}, no la reemplaza.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario_motivos_cambio")
public class UsuarioMotivoCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    // 'CAMBIO_ESTADO' o 'ELIMINACION' -- restringido en el motor por un
    // CHECK (V50); este mapeo no lo repite en Java.
    @Column(name = "tipo_cambio", nullable = false, length = 20)
    private String tipoCambio;

    @Column(name = "estado_anterior")
    private Integer estadoAnterior;

    @Column(name = "estado_nuevo")
    private Integer estadoNuevo;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "ejecutado_por", nullable = false)
    private Long ejecutadoPor;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;
}
