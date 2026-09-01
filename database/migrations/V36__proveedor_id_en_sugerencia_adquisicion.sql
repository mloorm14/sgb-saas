-- V36: vínculo opcional sugerencia_adquisicion -> proveedores (nullable, aditivo).
-- No modifica datos existentes, solo agrega columna nullable + FK.

ALTER TABLE sugerencias_adquisicion
    ADD COLUMN IF NOT EXISTS proveedor_id INTEGER REFERENCES proveedores(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sugerencia_proveedor ON sugerencias_adquisicion (proveedor_id);
