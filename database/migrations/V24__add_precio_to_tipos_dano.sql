-- ============================================================================
-- V24__add_precio_to_tipos_dano.sql
--
-- Corrección: la tabla tipos_dano puede existir sin la columna precio
-- (creada manualmente o desde un schema.sql incompleto). Se agrega la
-- columna si falta, alineando la BD con la entidad JPA TipoDano.
-- ============================================================================

ALTER TABLE tipos_dano
    ADD COLUMN IF NOT EXISTS precio NUMERIC(8,2) NOT NULL DEFAULT 0;
