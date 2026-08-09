package com.uteq.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Mapea la tabla {@code notificaciones} 1:1, sin lógica de negocio (mismo
 * criterio que {@link Multa}/{@link Prestamo}: {@code usuarioId},
 * {@code prestamoId} y {@code tipoNotificacionId} se exponen como
 * identificadores planos, sin {@code @ManyToOne}, para mantener el
 * repositorio CRUD libre de joins).
 * <p>
 * {@code prestamoId} es nullable a propósito: una notificación de tipo
 * MULTA (por daño, no por atraso) o RESERVA_CADUCADA no siempre tiene un
 * préstamo de origen. {@code enviadoOk}/{@code errorEnvio} registran el
 * resultado real del envío -- ver {@code EmailService}, que nunca debe
 * propagar un fallo de SMTP como error 500 al flujo de préstamo/devolución
 * que la origina, sino dejarlo trazado acá.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "prestamo_id")
    private Long prestamoId;

    @Column(name = "tipo_notificacion_id", nullable = false)
    private Integer tipoNotificacionId;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "enviado_ok", nullable = false)
    private boolean enviadoOk;

    @Column(name = "error_envio", length = 255)
    private String errorEnvio;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;
}
