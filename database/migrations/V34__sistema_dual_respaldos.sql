-- V34__sistema_dual_respaldos.sql
-- Migración para soportar el diseño dual de respaldos (Completos vía DR y Exportaciones Selectivas)

-- 1. Tablas para Backups Completos (Disaster Recovery - Node.js)
CREATE TABLE IF NOT EXISTS configuracion_respaldo (
    id BIGSERIAL PRIMARY KEY,
    habilitado BOOLEAN DEFAULT true,
    frecuencia_horas INTEGER NOT NULL DEFAULT 6,
    dias_retencion INTEGER NOT NULL DEFAULT 14,
    ultima_ejecucion TIMESTAMPTZ,
    proxima_ejecucion TIMESTAMPTZ,
    actualizado_por BIGINT REFERENCES usuarios(id),
    actualizado_en TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS registros_respaldo (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,          -- 'automatico' o 'manual'
    estado VARCHAR(20) NOT NULL,        -- 'exitoso', 'fallido', 'ejecutando'
    nombre_archivo VARCHAR(255),
    tamano_archivo_bytes BIGINT,
    ruta_r2 TEXT,
    mensaje_error TEXT,
    ejecutado_por BIGINT REFERENCES usuarios(id),
    iniciado_en TIMESTAMPTZ DEFAULT now(),
    finalizado_en TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_registros_respaldo_tipo ON registros_respaldo(tipo);
CREATE INDEX IF NOT EXISTS idx_registros_respaldo_iniciado_en ON registros_respaldo(iniciado_en DESC);

-- 2. Modificaciones a las tablas existentes para Exportaciones Selectivas (Spring Boot)
-- Se añade la columna 'tipo' ('manual' o 'automatico') a backups para separar historiales.
ALTER TABLE backups ADD COLUMN IF NOT EXISTS tipo VARCHAR(20);
UPDATE backups SET tipo = 'manual' WHERE tipo IS NULL;
ALTER TABLE backups ALTER COLUMN tipo SET NOT NULL;

-- Tabla relacional para guardar las tablas seleccionadas en la exportación automática
CREATE TABLE IF NOT EXISTS backup_programacion_tablas (
    programacion_id BIGINT NOT NULL REFERENCES backup_programacion(id) ON DELETE CASCADE,
    tabla VARCHAR(50) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_backup_programacion_tablas_prog ON backup_programacion_tablas(programacion_id);
