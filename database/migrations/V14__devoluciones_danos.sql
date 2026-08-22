-- ============================================================================
-- V14__devoluciones_danos.sql
--
-- Módulo de devoluciones con registro de daños:
-- - tipos_dano: catálogo de tipos de daño con precio fijado
-- - registro_danos: registro principal de cada devolución
-- - registro_dano_detalle: tipos de daño seleccionados por registro
-- - evidencia_dano: imágenes fotográficas del daño
-- - multas: se agrega registro_dano_id para vincular multa por daño
-- ============================================================================

-- ===== Catálogo de tipos de daño =====
CREATE TABLE tipos_dano (
    id       SERIAL PRIMARY KEY,
    nombre   VARCHAR(50) NOT NULL UNIQUE,
    precio   NUMERIC(8,2) NOT NULL CHECK (precio >= 0),
    activo   BOOLEAN NOT NULL DEFAULT TRUE
);

-- Seeds: tipos de daño predefinidos (precios iniciales, editables por admin)
INSERT INTO tipos_dano (nombre, precio) VALUES
    ('Paginas rotas', 3.00),
    ('Manchas', 5.00),
    ('Portada/Lomo', 7.00),
    ('Humedad', 10.00),
    ('Rayon', 4.00)
ON CONFLICT (nombre) DO NOTHING;

-- ===== Registro de devoluciones =====
CREATE TABLE registro_danos (
    id                BIGSERIAL PRIMARY KEY,
    prestamo_id       BIGINT NOT NULL REFERENCES prestamos(id) ON DELETE RESTRICT,
    estado_devolucion VARCHAR(20) NOT NULL CHECK (estado_devolucion IN ('BUEN_ESTADO', 'CON_DANO', 'PERDIDO')),
    descripcion       TEXT,
    bibliotecario_id  BIGINT NOT NULL REFERENCES usuarios(id),
    fecha_registro    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Un préstamo solo puede tener un registro de devolución
CREATE UNIQUE INDEX uq_registro_danos_prestamo ON registro_danos(prestamo_id);

-- ===== Detalle: tipos de daño seleccionados por registro =====
CREATE TABLE registro_dano_detalle (
    id                   BIGSERIAL PRIMARY KEY,
    registro_dano_id     BIGINT NOT NULL REFERENCES registro_danos(id) ON DELETE CASCADE,
    tipo_dano_id         INTEGER REFERENCES tipos_dano(id),
    nombre_custom        VARCHAR(100),
    precio_cobrado       NUMERIC(8,2) NOT NULL CHECK (precio_cobrado >= 0),
    CHECK (tipo_dano_id IS NOT NULL OR nombre_custom IS NOT NULL)
);

-- ===== Evidencia fotográfica =====
CREATE TABLE evidencia_dano (
    id                BIGSERIAL PRIMARY KEY,
    registro_dano_id  BIGINT NOT NULL REFERENCES registro_danos(id) ON DELETE CASCADE,
    archivo_nombre    VARCHAR(255) NOT NULL,
    archivo_tipo      VARCHAR(100) NOT NULL,
    archivo_bytes     BYTEA NOT NULL,
    subido_en         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ===== Vincular multas con registro de daño =====
ALTER TABLE multas ADD COLUMN registro_dano_id BIGINT REFERENCES registro_danos(id);
