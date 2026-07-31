-- ============================================================================
-- SGB-SaaS — db/fixes-schema.sql
-- Correcciones puntuales al schema principal (db/schema.sql), detectadas
-- durante la revisión de db/roles-privilegios.sql (materia de Administración
-- de BD). Van en un archivo separado porque son ALTER TABLE/CREATE INDEX
-- sobre las tablas de negocio, no sobre roles/privilegios de PostgreSQL.
--
-- ESTADO: NO ejecutado contra ningún Postgres real todavía. Pendiente de
-- fusionarse a db/schema.sql (y, si aplica, a una migración Flyway nueva en
-- database/migrations/) en un prompt posterior.
-- ============================================================================

-- FIX: CHECK constraint faltante en reservaciones. Sin esta constraint, el
-- schema permite insertar una reservación con fecha_limite_retiro anterior
-- (o igual) a fecha_reserva, lo cual no tiene sentido de negocio: el plazo
-- límite para retirar el libro reservado debe ser siempre posterior al
-- momento en que se hizo la reservación.
ALTER TABLE reservaciones
    ADD CONSTRAINT chk_fecha_limite_posterior
    CHECK (fecha_limite_retiro > fecha_reserva);

-- FIX: índices faltantes para los jobs periódicos de expiración de
-- reservaciones y detección de préstamos vencidos. Ambos jobs hacen un WHERE
-- frecuente combinando la columna de estado con la columna de fecha
-- correspondiente (ej. "reservaciones en estado PENDIENTE cuya
-- fecha_limite_retiro ya pasó" / "préstamos en estado ACTIVO cuya
-- fecha_devolucion_estimada ya pasó"); sin índice compuesto, cada corrida
-- del job fuerza un seq scan completo sobre la tabla.
CREATE INDEX idx_reservaciones_estado_fecha
    ON reservaciones (estado_reservacion_id, fecha_limite_retiro);

CREATE INDEX idx_prestamos_estado_fecha_estimada
    ON prestamos (estado_prestamo_id, fecha_devolucion_estimada);
