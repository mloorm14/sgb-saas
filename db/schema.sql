-- ============================================================================
-- SGB-SaaS — db/schema.sql
-- Snapshot consolidado del modelo de datos (24 tablas) para reproducibilidad
-- desde cero vía docker-entrypoint-initdb.d/. Ver docs/adr/adr-006-estrategia-schema-reproducible.md
-- para la justificación de por qué este archivo coexiste con
-- database/migrations/ (Flyway sigue siendo el mecanismo real de
-- versionado incremental; este archivo NO reemplaza a Flyway).
--
-- Este archivo resulta de fusionar:
--   - database/migrations/V1__schema_inicial.sql (Entrega 1B, 5 tablas)
--   - el schema de 24 tablas del módulo de Administración de BD
-- Discrepancias entre ambas fuentes revisadas y decididas por el equipo
-- (sin migración de datos: entorno de desarrollo, sin usuarios reales;
-- `activo BOOLEAN` eliminado de `usuarios`/`libros` en favor de `estado_id`).
-- Ver los comentarios "NOTA DE FUSIÓN" bajo `usuarios` y `libros` más abajo.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- MÓDULO 1: SEGURIDAD (8 tablas)
-- ============================================================================
CREATE TABLE estados_usuario (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(30)  NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

CREATE TABLE permisos (
    id     SERIAL PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE rol_permisos (
    rol_id     INTEGER NOT NULL REFERENCES roles(id)    ON DELETE CASCADE,
    permiso_id INTEGER NOT NULL REFERENCES permisos(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
);

-- NOTA DE FUSIÓN (usuarios): database/migrations/V1__schema_inicial.sql
-- (Entrega 1B) define esta tabla de forma distinta:
--   - tenía columna `rol VARCHAR(20)` embebida con CHECK
--     (ROLE_LECTOR/ROLE_BIBLIOTECARIO/ROLE_GERENTE) en vez de las tablas
--     normalizadas roles/usuario_roles de abajo.
--   - tenía columna `activo BOOLEAN` en vez de `estado_id` (FK a
--     estados_usuario, que además distingue 4 estados en vez de 2).
--   - la columna de fecha se llamaba `creado_en`, aquí es `fecha_registro`.
--   - no existían `apellido`, `identificacion_usuario`, `correo_verificado`.
-- Decisión del equipo: sin migración de datos (V1 solo tenía datos de
-- prueba en desarrollo, sin usuarios reales); se recrea la BD desde este
-- archivo + db/seed.sql. `correo` no lleva UNIQUE inline: la unicidad se
-- exige únicamente vía `idx_usuarios_correo` (evita índice duplicado).
CREATE TABLE usuarios (
    id                     BIGSERIAL PRIMARY KEY,
    nombre                 VARCHAR(100) NOT NULL,
    apellido               VARCHAR(100) NOT NULL,
    correo                 VARCHAR(150) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    identificacion_usuario VARCHAR(20),
    estado_id              INTEGER NOT NULL REFERENCES estados_usuario(id) ON DELETE RESTRICT,
    correo_verificado      BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_usuarios_correo ON usuarios (correo);

CREATE TABLE usuario_roles (
    usuario_id BIGINT  NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    rol_id     INTEGER NOT NULL REFERENCES roles(id)    ON DELETE RESTRICT,
    asignado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE tokens_invalidos (
    id          BIGSERIAL PRIMARY KEY,
    jti         VARCHAR(100) NOT NULL UNIQUE,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expira_en   TIMESTAMPTZ NOT NULL,
    invalidado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE verificaciones_correo (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token       UUID NOT NULL DEFAULT uuid_generate_v4(),
    expira_en   TIMESTAMPTZ NOT NULL,
    usado       BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- MÓDULO 2: CATÁLOGO E INVENTARIO (8 tablas)
-- ============================================================================

-- NOTA DE FUSIÓN (editoriales): V1 no tenía la columna `pais_origen`
-- (nullable, aditiva — sin conflicto).
CREATE TABLE editoriales (
    id           SERIAL PRIMARY KEY,
    nombre       VARCHAR(150) NOT NULL UNIQUE,
    pais_origen  VARCHAR(80)
);

CREATE TABLE idiomas (
    id         SERIAL PRIMARY KEY,
    nombre     VARCHAR(50) NOT NULL UNIQUE,
    codigo_iso VARCHAR(5)  NOT NULL UNIQUE
);

CREATE TABLE estados_libro (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
    -- Valores esperados (ver db/seed.sql): 'ACTIVO', 'DADO_DE_BAJA',
    -- 'EN_REPARACION', 'PERDIDO'
);

CREATE TABLE categorias (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE autores (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL
);

-- NOTA DE FUSIÓN (libros): respecto a V1:
--   - V1 tenía además una columna `activo BOOLEAN DEFAULT TRUE`, redundante
--     con `estado_id` (que ya distinguía ACTIVO/DADO_DE_BAJA/...). Decisión
--     del equipo: confirmado, se elimina (no se reintroduce).
--   - la columna de fecha se llamaba `creado_en`, aquí es `fecha_registro`.
--   - se agrega `ubicacion_fisica` (nueva, nullable, sin conflicto).
--   - el CHECK `stock_disponible <= stock_total` estaba inline en V1
--     (`chk_stock_disponible`); aquí es una constraint de tabla al final
--     (`chk_stock`) con el mismo efecto.
--   - `isbn` no lleva UNIQUE inline: la unicidad se exige únicamente vía
--     `idx_libros_isbn` (evita índice duplicado).
CREATE TABLE libros (
    id                BIGSERIAL PRIMARY KEY,
    isbn              VARCHAR(13)   NOT NULL,
    titulo            VARCHAR(255)  NOT NULL,
    resumen           TEXT,
    portada_url       VARCHAR(1000),
    anio_publicacion  SMALLINT      NOT NULL CHECK (anio_publicacion BETWEEN 1000 AND 2100),
    editorial_id      INTEGER       NOT NULL REFERENCES editoriales(id)   ON DELETE RESTRICT,
    idioma_id         INTEGER       NOT NULL REFERENCES idiomas(id)       ON DELETE RESTRICT,
    estado_id         INTEGER       NOT NULL REFERENCES estados_libro(id) ON DELETE RESTRICT,
    stock_total       SMALLINT      NOT NULL DEFAULT 1 CHECK (stock_total >= 0),
    stock_disponible  SMALLINT      NOT NULL DEFAULT 1 CHECK (stock_disponible >= 0),
    ubicacion_fisica  VARCHAR(50),
    fecha_registro    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    actualizado_en    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_stock CHECK (stock_disponible <= stock_total)
);

CREATE UNIQUE INDEX idx_libros_isbn ON libros (isbn);

CREATE TABLE libro_categorias (
    libro_id     BIGINT  NOT NULL REFERENCES libros(id)     ON DELETE CASCADE,
    categoria_id INTEGER NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
    PRIMARY KEY (libro_id, categoria_id)
);

CREATE TABLE libro_autores (
    libro_id  BIGINT NOT NULL REFERENCES libros(id)  ON DELETE CASCADE,
    autor_id  BIGINT NOT NULL REFERENCES autores(id) ON DELETE RESTRICT,
    PRIMARY KEY (libro_id, autor_id)
);

-- ============================================================================
-- MÓDULO 3: TRANSACCIONES (5 tablas)
-- ============================================================================
CREATE TABLE configuracion_sistema (
    clave  VARCHAR(50) PRIMARY KEY,
    valor  VARCHAR(200) NOT NULL
);

CREATE TABLE estados_prestamo (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE estados_multa (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE estados_reservacion (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE reservaciones (
    id                     BIGSERIAL PRIMARY KEY,
    usuario_id             BIGINT  NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    libro_id               BIGINT  NOT NULL REFERENCES libros(id)   ON DELETE RESTRICT,
    estado_reservacion_id  INTEGER NOT NULL REFERENCES estados_reservacion(id),
    fecha_reserva          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_limite_retiro    TIMESTAMPTZ NOT NULL
);

CREATE TABLE prestamos (
    id                          BIGSERIAL PRIMARY KEY,
    usuario_id                  BIGINT  NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    libro_id                    BIGINT  NOT NULL REFERENCES libros(id)   ON DELETE RESTRICT,
    bibliotecario_id            BIGINT  NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    reservacion_id              BIGINT  REFERENCES reservaciones(id) ON DELETE SET NULL,
    fecha_prestamo              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_devolucion_estimada   TIMESTAMPTZ NOT NULL,
    fecha_devolucion_real       TIMESTAMPTZ,
    renovaciones_realizadas     SMALLINT NOT NULL DEFAULT 0 CHECK (renovaciones_realizadas >= 0),
    estado_prestamo_id          INTEGER NOT NULL REFERENCES estados_prestamo(id)
);

-- DECISIÓN DE NEGOCIO (resuelta): un préstamo SÍ puede generar más de una
-- multa (ej. daño al libro + atraso, registrados por separado). Por eso
-- prestamo_id NO lleva UNIQUE -- ver database/migrations/V3__multas_multiples_por_prestamo.sql
-- para el DROP CONSTRAINT sobre bases ya existentes con el UNIQUE previo.
CREATE TABLE multas (
    id               BIGSERIAL PRIMARY KEY,
    prestamo_id      BIGINT NOT NULL REFERENCES prestamos(id) ON DELETE RESTRICT,
    monto            NUMERIC(8,2) NOT NULL CHECK (monto > 0),
    estado_multa_id  INTEGER NOT NULL REFERENCES estados_multa(id),
    fecha_generada   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_pagada     TIMESTAMPTZ,
    observaciones    VARCHAR(255)
);

-- ============================================================================
-- MÓDULO 4: EXPERIENCIA DEL LECTOR (2 tablas)
-- ============================================================================
-- NOTA: favoritos y sugerencias_adquisicion exigen usuario_id NOT NULL en
-- ambas tablas — a propósito, no es un descuido. Ambas funcionalidades
-- requieren usuario autenticado; el portal anónimo (visitante sin login,
-- solo landing + catálogo público) NO tiene persistencia de favoritos ni
-- puede enviar sugerencias de adquisición. Es una decisión de producto ya
-- tomada implícitamente por el diseño del schema; se documenta aquí para
-- que quede explícita.
CREATE TABLE favoritos (
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    libro_id    BIGINT NOT NULL REFERENCES libros(id)   ON DELETE CASCADE,
    agregado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_id, libro_id)
);

CREATE TABLE sugerencias_adquisicion (
    id           BIGSERIAL PRIMARY KEY,
    usuario_id   BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    titulo       VARCHAR(255) NOT NULL,
    autor        VARCHAR(150),
    isbn         VARCHAR(13),
    justificacion TEXT,
    estado       VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                 CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA')),
    revisado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- MÓDULO 5: AUDITORÍA (1 tabla)
-- ============================================================================
CREATE TABLE bitacora_auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    tipo_operacion  VARCHAR(20) NOT NULL
                    CHECK (tipo_operacion IN
                           ('INSERT','UPDATE','DELETE','LOGIN_OK','LOGIN_FAIL','LOGOUT')),
    tabla_afectada  VARCHAR(50) NOT NULL,
    registro_id     BIGINT,
    detalles        TEXT NOT NULL,
    ip_origen       VARCHAR(45),
    fecha_hora      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- TRIGGERS DE actualizado_en
-- ============================================================================
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

-- ============================================================================
-- FIN DEL SCHEMA — 24 TABLAS
-- ============================================================================
