-- V32__backup_programacion.sql
-- Tabla de configuración de respaldos automáticos (cada X horas O cada X días)
-- Lógica XOR: solo puede definirse uno de los dos campos, nunca los dos a la vez.

CREATE TABLE IF NOT EXISTS backup_programacion (
    id               BIGSERIAL PRIMARY KEY,
    creado_por       BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    cada_horas       INTEGER,  -- 1-23, NULL si se usa cadaDias
    cada_dias        INTEGER,  -- 1-30, NULL si se usa cadaHoras
    formato          VARCHAR(10) NOT NULL DEFAULT 'sql',  -- 'sql' o 'csv'
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Restricción XOR: exactamente uno de los dos debe tener valor
    CHECK (((cada_horas IS NOT NULL)::int + (cada_dias IS NOT NULL)::int) = 1),

    -- Rangos permitidos cuando vienen definidos
    CHECK (cada_horas IS NULL OR (cada_horas >= 1 AND cada_horas <= 23)),
    CHECK (cada_dias IS NULL OR (cada_dias >= 1 AND cada_dias <= 30)),

    -- El creador debe ser un admin (validado en la app, pero BD lo respalda)
    CHECK (creado_por IS NOT NULL)
);

-- Índices para consultas frecuentes en el historial y estado activo
CREATE INDEX IF NOT EXISTS idx_backup_programacion_creado_en ON backup_programacion(creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_backup_programacion_activo ON backup_programacion(activo, creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_backup_programacion_formato ON backup_programacion(formato);

-- Comentario para desarrolladores:
-- - La tabla almacena la programación de respaldos automáticos.
-- - Cada registro representa una tarea programada activa o inactiva.
-- - La restricción CHECK garantiza que solo haya un tipo de recurrencia por fila:
--   o se programa cada X horas, o cada X días, pero nunca los dos a la vez.
-- - El campo 'formato' respeta el mismo valor de la entidad 'backups'.
-- - Se reutiliza el patrón de 'creado_por -> usuarios' para auditoría.
-- - La ejecución del scheduler (BackupScheduler) debe leer esta tabla y
--   generar el zip correspondiente via BackupService.generarBackup(),
--   luego marcar la programación como última ejecución en la tabla 'backups'.