-- V2__rbac_normalizado.sql
-- Evolución incremental real (Flyway) del esquema de Entrega 1B hacia el
-- modelo RBAC normalizado que ya vive en db/schema.sql: reemplaza la
-- columna de texto libre `usuarios.rol` por las tablas `roles` +
-- `usuario_roles` (relación muchos-a-muchos), y la columna `activo`
-- (en usuarios y libros) por una referencia a un catálogo de estados
-- (`estado_id`).
--
-- ALCANCE: solo el cambio estructural de seguridad/RBAC (de ahí el nombre
-- del archivo). Las demás tablas nuevas de db/schema.sql (préstamos,
-- multas, reservaciones, auditoría, permisos, etc.) NO se incluyen aquí:
-- se versionarán en migraciones V3+ separadas cuando esa funcionalidad
-- esté lista para aplicarse incrementalmente sobre una base con datos
-- reales. Mezclarlas aquí habría hecho este archivo un segundo "snapshot
-- completo" en vez de una evolución incremental real.
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
