-- V33: agrega columna ultima_ejecucion a backup_programacion
-- La entidad BackupProgramacion.java la referencia como @Column(name = "ultima_ejecucion")
-- Esta migración es idempotente (IF NOT EXISTS).

ALTER TABLE backup_programacion ADD COLUMN IF NOT EXISTS ultima_ejecucion TIMESTAMPTZ;

COMMENT ON COLUMN backup_programacion.ultima_ejecucion IS 'Fecha/hora de la última ejecución automática programada. NULL si nunca ejecutó.';