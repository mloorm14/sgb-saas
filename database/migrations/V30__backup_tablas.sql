-- V30__backup_tablas.sql
-- Tabla de historial de backups/dumps generados
-- Soporta el módulo de respaldos con filtros por fecha y tabla

CREATE TABLE IF NOT EXISTS backups (
    id          BIGSERIAL PRIMARY KEY,
    creado_por  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    desde       TIMESTAMPTZ NOT NULL,
    hasta       TIMESTAMPTZ NOT NULL,
    formato     VARCHAR(10) NOT NULL DEFAULT 'sql',  -- 'sql' o 'csv'
    estado      VARCHAR(20) NOT NULL DEFAULT 'COMPLETADO',  -- 'COMPLETADO' | 'FALLIDO'
    tamano_bytes BIGINT,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Restricción para evitar rangos absurdos (aplicada en la app, pero
    -- también a nivel de BD para consistencia):
    CHECK (desde < hasta),
    CHECK (hasta <= now()),  -- no backups del futuro
    CHECK (tamano_bytes IS NULL OR tamano_bytes >= 0)
);

-- Índices para consultas frecuentes en el historial
CREATE INDEX IF NOT EXISTS idx_backups_creado_en ON backups(creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_backups_desde_hasta ON backups(desde, hasta);
CREATE INDEX IF NOT EXISTS idx_backups_estado ON backups(estado);
CREATE INDEX IF NOT EXISTS idx_backups_creadoPor ON backups(creado_por);

-- Comentario para desarrolladores:
-- - La columna 'tablas' en sí no se almacena aquí (se normalizaría en
--   una tabla hija o array si se requiere consultar por tabla específica).
-- - El campo 'ruta' (path o URL S3) se gestiona en la aplicación; el
--   administrador debe asegurar la retención según BACKUP_RETENTION_DAYS.
-- - Validación de rango 30 días: se aplica en BackupService.validarRango().