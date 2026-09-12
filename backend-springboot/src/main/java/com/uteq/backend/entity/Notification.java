package com.uteq.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
 * criterio que {@link Fine}/{@link Loan}: {@code usuarioId},
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
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("usuarioId")
    @Column(name = "usuario_id", nullable = false)  private Long userId;

    @Column(name = "prestamo_id")
    private Long loanId;

    @JsonProperty("tipoNotificacionId")
    @Column(name = "tipo_notificacion_id", nullable = false)  private Integer typeNotificationId;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String message;

    @JsonProperty("fechaEnvio")
        @Column(name = "fecha_envio")  private OffsetDateTime dateEnvio;

    @Column(name = "enviado_ok", nullable = false)
    private boolean enviadoOk;

    @Column(name = "error_envio", length = 255)
    private String errorEnvio;

    @Column(name = "creado_en", nullable = false)
    @JsonProperty("creadoEn")
    private OffsetDateTime created;
}
