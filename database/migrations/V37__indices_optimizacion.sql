-- V37 — Indices ADB (migrar 3 CREATE INDEX de db/optimizacion-consultas.sql)
-- Nota: V29 nunca existio (salto V28 -> V30 verificado con git log --all -- "*V29*" vacio). Baseline production real es V36.
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha_hora ON bitacora_auditoria(fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_prestamos_usuario_id ON prestamos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_prestamos_estado_fecha_devolucion ON prestamos(estado_prestamo_id, fecha_devolucion_estimada);
