-- V2__rbac_normalizado.sql
-- Evolución incremental real (Flyway) del esquema de Entrega 1B hacia el
-- modelo RBAC normalizado que ya vive en db/schema.sql: reemplaza la
-- columna de texto libre `usuarios.rol` por las tablas `roles` +
-- `usuario_roles` (relación muchos-a-muchos), y la columna `activo`
-- (en usuarios y libros) por una referencia a un catálogo de estados
-- (`estado_id`).
--
-- ALCANCE: además del refactor de seguridad/RBAC (de ahí el nombre del
-- archivo), esta migración cierra la brecha entre el esquema V1 (5 tablas
-- de la Entrega 1B) y el esquema final de 24 tablas de db/schema.sql.
-- HISTORIA: el plan original versionaba las tablas restantes (préstamos,
-- multas, reservaciones, auditoría, permisos, etc.) en migraciones V3+
-- separadas. Ese versionado incremental por módulo nunca se materializó —
-- V3+ terminaron siendo deltas de funcionalidad nueva, no la creación de
-- las tablas base — y en un arranque genuinamente vacío (Flyway real sobre
-- Neon, sin pasar por docker-entrypoint-initdb.d/db/schema.sql) la cadena
-- V1→V9 rompía: V3 asumía `multas` y V4/V6/V7 asumían
-- `configuracion_sistema`, `prestamos` y `bitacora_auditoria`, tablas que
-- ninguna migración creaba jamás (ver README/despliegue). Por eso las 18
-- tablas restantes de db/schema.sql se incorporan AQUÍ, verbatim desde ese
-- archivo y en orden de dependencias (los catálogos antes que las tablas
-- que los referencian). A partir de V3, las migraciones son deltas reales
-- sobre el esquema completo. En entornos sembrados desde db/schema.sql
-- (docker-entrypoint-initdb.d, ver Makefile) esta migración NO se ejecuta:
-- baseline-on-migrate + baseline-version (application.yml) la dan por
-- aplicada — ver docs/adr/adr-006-estrategia-schema-reproducible.md.
--
-- Esta migración asume que puede correr sobre una base V1 con datos
-- existentes: por eso cada DROP COLUMN va precedido de una migración
-- explícita de los datos que esa columna representaba (INSERT ... SELECT /
-- UPDATE), nunca de un DROP directo.
--
-- En un entorno fresco levantado desde db/schema.sql + db/seed.sql
-- (docker-entrypoint-initdb.d, ver Makefile/scripts/build-init-sql.sql)
-- esta migración NO se ejecuta: `spring.flyway.baseline-on-migrate: true`
-- (application.yml) hace que Flyway, al encontrar un esquema no vacío sin
-- tabla de historial, establezca el baseline en la versión más alta
-- presente en database/migrations/ y no reintente aplicar nada por debajo
-- de ella. Ver docs/adr/adr-006-estrategia-schema-reproducible.md.

-- ============================================================================
-- 1. Catálogos nuevos
-- ============================================================================
CREATE TABLE estados_usuario (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO estados_usuario (nombre) VALUES
    ('ACTIVO'),
    ('BLOQUEADO_POR_MULTA'),
    ('INACTIVO'),
    ('PENDIENTE_VERIFICACION');

CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(30)  NOT NULL UNIQUE,
    descripcion VARCHAR(200)
);

INSERT INTO roles (nombre, descripcion) VALUES
    ('LECTOR',        'Usuario final: consulta catálogo, reserva y solicita préstamos'),
    ('BIBLIOTECARIO', 'Gestiona préstamos, devoluciones, reservas y multas'),
    ('GERENTE',       'Gestiona catálogo, inventario y reportes'),
    ('ADMIN',         'Administración total del sistema, usuarios y roles');

CREATE TABLE usuario_roles (
    usuario_id  BIGINT  NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    rol_id      INTEGER NOT NULL REFERENCES roles(id)    ON DELETE RESTRICT,
    asignado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_id, rol_id)
);

-- ============================================================================
-- 2. usuarios: columnas nuevas
-- ============================================================================
ALTER TABLE usuarios ADD COLUMN apellido VARCHAR(100);
UPDATE usuarios SET apellido = 'SIN_APELLIDO' WHERE apellido IS NULL;
ALTER TABLE usuarios ALTER COLUMN apellido SET NOT NULL;

ALTER TABLE usuarios ADD COLUMN identificacion_usuario VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN correo_verificado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN estado_id INTEGER REFERENCES estados_usuario(id) ON DELETE RESTRICT;

-- Migrar el booleano `activo` viejo a estado_id (ANTES de dropear `activo`).
UPDATE usuarios u
   SET estado_id = (
       SELECT id FROM estados_usuario
        WHERE nombre = CASE WHEN u.activo THEN 'ACTIVO' ELSE 'INACTIVO' END
   );

ALTER TABLE usuarios ALTER COLUMN estado_id SET NOT NULL;

-- Migrar el texto `rol` viejo (ROLE_LECTOR/ROLE_BIBLIOTECARIO/ROLE_GERENTE)
-- a usuario_roles (ANTES de dropear `rol`). El CHECK chk_rol de V1 no
-- permitía ningún otro valor, así que quitar el prefijo "ROLE_" siempre
-- resuelve a un nombre existente en el catálogo `roles` recién sembrado.
INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id
  FROM usuarios u
  JOIN roles r ON r.nombre = REPLACE(u.rol, 'ROLE_', '')
 WHERE u.rol IS NOT NULL;

-- ============================================================================
-- 3. usuarios: eliminar columnas viejas (ya migradas arriba) y renombrar
-- ============================================================================
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_rol;
ALTER TABLE usuarios DROP COLUMN rol;
ALTER TABLE usuarios DROP COLUMN activo;
ALTER TABLE usuarios RENAME COLUMN creado_en TO fecha_registro;

-- correo ya era UNIQUE por índice (idx_usuarios_correo) desde V1; se deja
-- igual (ver NOTA DE FUSIÓN en db/schema.sql sobre por qué ese índice no
-- se duplica con un UNIQUE inline en la columna).

-- ============================================================================
-- 4. libros: columna nueva, columna vieja fuera, y renombrado
-- ============================================================================
ALTER TABLE libros ADD COLUMN ubicacion_fisica VARCHAR(50);

-- `activo` en libros era redundante con estado_id (que ya distinguía
-- ACTIVO/DADO_DE_BAJA/EN_REPARACION/PERDIDO desde V1) — no requiere
-- migración de datos, estado_id ya es la fuente de verdad.
ALTER TABLE libros DROP COLUMN activo;
ALTER TABLE libros RENAME COLUMN creado_en TO fecha_registro;

-- ============================================================================
-- 5. Tablas restantes del esquema de 24 tablas (db/schema.sql, verbatim)
-- ============================================================================
-- Ver el ALCANCE arriba: la cadena Flyway debe ser auto-suficiente sobre
-- una base genuinamente vacía (Render/Neon), donde db/schema.sql nunca
-- corre. Definiciones copiadas tal cual de db/schema.sql (fuente de verdad
-- del esquema final) — incluidas las NOTA DE FUSIÓN que documentan las
-- diferencias deliberadas con respecto a V1.

-- ===== MÓDULO 1 (continuación): SEGURIDAD (permisos, tokens, verificación)
CREATE TABLE permisos (
    id     SERIAL PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE rol_permisos (
    rol_id     INTEGER NOT NULL REFERENCES roles(id)    ON DELETE CASCADE,
    permiso_id INTEGER NOT NULL REFERENCES permisos(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
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

-- ===== MÓDULO 2 (continuación): CATÁLOGO E INVENTARIO (categorías/autores)
CREATE TABLE categorias (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE autores (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL
);

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

-- ===== MÓDULO 3: TRANSACCIONES (configuración, estados y préstamos/multas)
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
-- prestamo_id NO lleva UNIQUE — db/schema.sql#L220 y
-- database/migrations/V3__multas_multiples_por_prestamo.sql marcan la
-- misma decisión; V3 queda como no-op para bases sembradas que llegaron a
-- tener el UNIQUE previo.
CREATE TABLE multas (
    id               BIGSERIAL PRIMARY KEY,
    prestamo_id      BIGINT NOT NULL REFERENCES prestamos(id) ON DELETE RESTRICT,
    monto            NUMERIC(8,2) NOT NULL CHECK (monto > 0),
    estado_multa_id  INTEGER NOT NULL REFERENCES estados_multa(id),
    fecha_generada   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_pagada     TIMESTAMPTZ,
    observaciones    VARCHAR(255)
);

-- ===== MÓDULO 4: EXPERIENCIA DEL LECTOR (favoritos, sugerencias)
-- NOTA: favoritos y sugerencias_adquisicion exigen usuario_id NOT NULL en
-- ambas tablas — a propósito (decisión de producto ya documentada en
-- db/schema.sql): ambas funcionalidades requieren usuario autenticado; el
-- portal anónimo no tiene persistencia de favoritos ni puede enviar
-- sugerencias de adquisición.
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

-- ===== MÓDULO 5: AUDITORÍA
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
