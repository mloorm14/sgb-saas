-- V31: completa V30 con columnas faltantes para entity Backup
ALTER TABLE backups ADD COLUMN IF NOT EXISTS ruta VARCHAR(500);
-- Si ya hay filas, ruta no puede quedar NULL; poner placeholder temporal
UPDATE backups SET ruta = 'local:pending' WHERE ruta IS NULL;
ALTER TABLE backups ALTER COLUMN ruta SET NOT NULL;

CREATE TABLE IF NOT EXISTS backups_tablas (
    backup_id BIGINT NOT NULL REFERENCES backups(id) ON DELETE CASCADE,
    tabla VARCHAR(50) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_backups_tablas_backup ON backups_tablas(backup_id);
