-- V38: F8-gerente — rastrear quién creó cada usuario para que GERENTE
-- vea solo los suyos y ADMIN siga viendo todo.
-- Históricos quedan NULL (= sistema/admin, sin filtro).
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS creado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_usuarios_creado_por ON usuarios (creado_por);
