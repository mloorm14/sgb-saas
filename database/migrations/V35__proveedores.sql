-- V35: catálogo de proveedores (catálogo de perfiles, solo GERENTE/ADMIN).
-- Tabla nueva, sin tocar tablas existentes con datos.

CREATE TABLE IF NOT EXISTS proveedores (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL UNIQUE,
    ruc VARCHAR(20) UNIQUE,
    direccion VARCHAR(255),
    telefono VARCHAR(30),
    email VARCHAR(150),
    persona_contacto VARCHAR(150),
    activo BOOLEAN NOT NULL DEFAULT true,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proveedores_nombre ON proveedores (nombre);
CREATE INDEX IF NOT EXISTS idx_proveedores_activo ON proveedores (activo);
