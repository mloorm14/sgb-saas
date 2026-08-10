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
import java.util.UUID;

/**
 * Mapea la tabla {@code sesiones_chat} (migración V9, Módulo H) 1:1, sin
 * lógica de negocio -- mismo criterio que {@link Notificacion}/
 * {@link Prestamo}: {@code usuarioId} se expone como identificador plano,
 * sin {@code @ManyToOne}.
 * <p>
 * Decisión del {@code id}: {@code @GeneratedValue(strategy = GenerationType.UUID)}
 * (Hibernate genera el UUID en la app) en vez de dejar que la BD lo genere
 * con {@code gen_random_uuid()} ({@code insertable = false}). Motivo:
 * ChatbotService necesita {@code sesion.getId()} INMEDIATAMENTE después de
 * crear la sesión para persistir los {@link MensajeChat} asociados en la
 * misma transacción. Con generación por BD, el id no quedaría poblado tras
 * {@code save()} y habría que forzar un flush+refresh. El default de la
 * columna se conserva como respaldo para inserts SQL directos, es
 * funcionalmente equivalente y no rompe la migración.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sesiones_chat")
public class SesionChat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "ultima_actividad", nullable = false)
    private OffsetDateTime ultimaActividad;
}
