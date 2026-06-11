-- V1__schema_inicial.sql
-- Flyway ejecuta este archivo en orden; nunca modificar
-- un archivo V ya ejecutado: crear V2__ en su lugar.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===== TABLAS MAESTRAS =====
CREATE TABLE editoriales (
    id   SERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE idiomas (
    id   SERIAL PRIMARY KEY,
    nombre     VARCHAR(50) NOT NULL UNIQUE,
    codigo_iso VARCHAR(5)  NOT NULL UNIQUE
);

CREATE TABLE estados_libro (
    id   SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
    -- Valores: 'ACTIVO', 'DADO_DE_BAJA', 'EN_REPARACION', 'PERDIDO'
);

-- ===== TABLA DE USUARIOS =====
CREATE TABLE usuarios (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    correo          VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    rol             VARCHAR(20)  NOT NULL DEFAULT 'ROLE_LECTOR',
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_usuarios_correo ON usuarios (correo);

ALTER TABLE usuarios
    ADD CONSTRAINT chk_rol
    CHECK (rol IN ('ROLE_LECTOR', 'ROLE_BIBLIOTECARIO', 'ROLE_GERENTE'));

-- ===== TABLA DE LIBROS =====
CREATE TABLE libros (
    id                BIGSERIAL PRIMARY KEY,
    isbn              VARCHAR(13)   NOT NULL,
    titulo            VARCHAR(255)  NOT NULL,
    resumen           TEXT,
    portada_url       VARCHAR(1000),
    anio_publicacion  SMALLINT      NOT NULL,
    editorial_id      INTEGER       NOT NULL REFERENCES editoriales(id) ON DELETE RESTRICT,
    idioma_id         INTEGER       NOT NULL REFERENCES idiomas(id) ON DELETE RESTRICT,
    estado_id         INTEGER       NOT NULL REFERENCES estados_libro(id) ON DELETE RESTRICT,
    stock_total       SMALLINT      NOT NULL DEFAULT 1,
    stock_disponible  SMALLINT      NOT NULL DEFAULT 1,
    activo            BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_anio_publicacion CHECK (anio_publicacion BETWEEN 1000 AND 2100),
    CONSTRAINT chk_stock_total CHECK (stock_total >= 0),
    CONSTRAINT chk_stock_disponible CHECK (stock_disponible >= 0 AND stock_disponible <= stock_total)
);

CREATE UNIQUE INDEX idx_libros_isbn ON libros (isbn);

-- ===== TRIGGER PARA actualizado_en =====
CREATE OR REPLACE FUNCTION set_actualizado_en()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuarios_actualizado_en
    BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

CREATE TRIGGER trg_libros_actualizado_en
    BEFORE UPDATE ON libros
    FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

-- [Las tablas de prestamos, multas, reservaciones, bitacora_auditoria
--  y el resto del esquema de 24 tablas se incorporaran en V2__ y
--  posteriores, conforme al cronograma de la Entrega 2]
