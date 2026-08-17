-- ============================================================================
-- SGB-SaaS — V10__seed_catalogos_y_admin.sql
-- Migración VERSIONADA (se aplica una sola vez por base).
--
-- FUENTE CANÓNICA: este archivo es el reflejo versionado de db/seed.sql
-- para despliegues sin make up (Render/Neon): docker-entrypoint-initdb.d
-- con build-init-sql.sh sigue siendo LA fuente de verdad del seed en
-- desarrollo local. Si se edita el seed (o este archivo), hay que reflejar
-- el cambio en el otro a mano — no hay generación automática entre ambos.
--
-- Por qué versionada y no repetible: los datos de seed no deben re-correr
-- solos cada vez que cambie el checksum del archivo (a diferencia de las
-- funciones de R__stored_procedures.sql, que sí son repetibles).
--
-- Idempotencia: en el flujo local (make up) la base ya nace sembrada vía
-- docker-entrypoint-initdb.d y Flyway baselinea en V3 — esta migración SÍ
-- se ejecuta después del baseline y debe tolerar filas ya existentes. Por
-- eso cada INSERT es ON CONFLICT DO NOTHING (o WHERE NOT EXISTS en tablas
-- sin constraint única, como autores).
--
-- Nota: estados_usuario y roles NO se siembran aquí — V2__rbac_normalizado.sql
-- ya los crea con sus filas, tanto en base vacía como en la sembrada.
-- ============================================================================

-- NOTA DE FUSIÓN (editoriales): V1__schema_inicial.sql creó editoriales SIN
-- la columna pais_origen — esa columna solo existe en db/schema.sql (el
-- snapshot), donde la "NOTA DE FUSIÓN (editoriales)" del propio archivo lo
-- documenta ("nullable, aditiva — sin conflicto"). En el flujo make up nunca
-- se nota (la tabla nace del snapshot completo), pero en la cadena Flyway
-- real el INSERT del seed la referenciaba y fallaba con 'column
-- "pais_origen" does not exist'. Se agrega acá (aditiva, nullable, IF NOT
-- EXISTS para la base sembrada donde ya existe) — mismo criterio que el
-- resto de esta rama: la cadena Flyway real debe quedar idéntica al snapshot.
ALTER TABLE editoriales ADD COLUMN IF NOT EXISTS pais_origen VARCHAR(80);

-- ===== Catálogos de estados (nombre UNIQUE -> ON CONFLICT) =====
INSERT INTO estados_libro (nombre) VALUES
    ('ACTIVO'),
    ('DADO_DE_BAJA'),
    ('EN_REPARACION'),
    ('PERDIDO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO estados_prestamo (nombre) VALUES
    ('ACTIVO'),
    ('RENOVADO'),
    ('DEVUELTO'),
    ('VENCIDO')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO estados_multa (nombre) VALUES
    ('PENDIENTE'),
    ('PAGADA'),
    ('ANULADA')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO estados_reservacion (nombre) VALUES
    ('PENDIENTE'),
    ('LISTA_PARA_RETIRO'),
    ('RETIRADA'),
    ('EXPIRADA'),
    ('CANCELADA')
ON CONFLICT (nombre) DO NOTHING;

-- ===== configuracion_sistema (clave PK -> ON CONFLICT) =====
-- 'monto_multa_diaria' es requerido por sp_registrar_devolucion (calcula
-- multas.monto = dias_atraso * este valor). Mismo placeholder que
-- db/seed.sql — ajustar al valor real que defina la biblioteca.
INSERT INTO configuracion_sistema (clave, valor) VALUES
    ('monto_multa_diaria', '0.50')
ON CONFLICT (clave) DO NOTHING;

-- ===== editoriales / idiomas / categorias (nombre UNIQUE) =====
INSERT INTO editoriales (nombre, pais_origen) VALUES
    ('Prentice Hall',    'Estados Unidos'),
    ('Addison-Wesley',   'Estados Unidos'),
    ('Debolsillo',       'España'),
    ('Debate',           'España'),
    ('Plaza & Janés',    'España')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO idiomas (nombre, codigo_iso) VALUES
    ('Español', 'es'),
    ('Inglés',  'en')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO categorias (nombre) VALUES
    ('Ficción'),
    ('Tecnología'),
    ('Historia')
ON CONFLICT (nombre) DO NOTHING;

-- ===== autores =====
-- Sin constraint UNIQUE sobre nombre (igual que db/schema.sql) -> guard
-- WHERE NOT EXISTS en vez de ON CONFLICT.
INSERT INTO autores (nombre)
SELECT nombre FROM (VALUES
    ('Robert C. Martin'),
    ('Martin Fowler'),
    ('Gabriel García Márquez'),
    ('Isabel Allende'),
    ('Yuval Noah Harari')
) AS seed(nombre)
WHERE NOT EXISTS (SELECT 1 FROM autores a WHERE a.nombre = seed.nombre);

-- ===== Usuario administrador =====
-- Mismo hash BCrypt (costo 12) que usa db/seed.sql — contraseña de
-- desarrollo Admin123! documentada ahí. CAMBIAR la contraseña en un
-- entorno real (ver README.md → "Credenciales de desarrollo").
-- correo es UNIQUE vía idx_usuarios_correo (no inline) -> ON CONFLICT (correo).
INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
VALUES (
    'Admin',
    'SGB',
    'admin@sgb-saas.local',
    '$2a$12$FIh2GQfhmm1nmqybVIIquuoL0xsLlbcL1oBQ74b6P0QXOwJQ34B8y',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
)
ON CONFLICT (correo) DO NOTHING;

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id
  FROM usuarios u, roles r
 WHERE u.correo = 'admin@sgb-saas.local'
   AND r.nombre = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ===== Libros de ejemplo (isbn UNIQUE vía idx_libros_isbn) =====
INSERT INTO libros (isbn, titulo, resumen, anio_publicacion, editorial_id, idioma_id, estado_id, stock_total, stock_disponible)
SELECT seed.isbn, seed.titulo, seed.resumen, seed.anio_publicacion,
       e.id, i.id, el.id, seed.stock_total, seed.stock_disponible
  FROM (VALUES
    ('9780132350884', 'Clean Code',
     'Guía de prácticas para escribir código legible y mantenible.', 2008,
     'Prentice Hall', 'en', 3, 3),
    ('9780134757599', 'Refactoring: Improving the Design of Existing Code',
     'Catálogo de técnicas para mejorar la estructura interna del código sin alterar su comportamiento.', 2018,
     'Addison-Wesley', 'en', 2, 2),
    ('9780307474728', 'Cien años de soledad',
     'Novela emblemática del realismo mágico latinoamericano.', 1967,
     'Debolsillo', 'es', 4, 4),
    ('9788499926223', 'Sapiens: De animales a dioses',
     'Recorrido por la historia de la humanidad desde la Edad de Piedra hasta la actualidad.', 2014,
     'Debate', 'es', 2, 2),
    ('9788401352836', 'La casa de los espíritus',
     'Saga familiar que combina historia política y elementos fantásticos.', 1982,
     'Plaza & Janés', 'es', 3, 3)
  ) AS seed(isbn, titulo, resumen, anio_publicacion, editorial, codigo_iso, stock_total, stock_disponible)
  JOIN editoriales e ON e.nombre = seed.editorial
  JOIN idiomas i     ON i.codigo_iso = seed.codigo_iso
  JOIN estados_libro el ON el.nombre = 'ACTIVO'
ON CONFLICT (isbn) DO NOTHING;

-- ===== Relaciones libro-autor / libro-categoría (PK compuesta -> ON CONFLICT DO NOTHING) =====
INSERT INTO libro_autores (libro_id, autor_id)
SELECT l.id, a.id
  FROM libros l, autores a
 WHERE (l.isbn = '9780132350884' AND a.nombre = 'Robert C. Martin')
    OR (l.isbn = '9780134757599' AND a.nombre = 'Martin Fowler')
    OR (l.isbn = '9780307474728' AND a.nombre = 'Gabriel García Márquez')
    OR (l.isbn = '9788499926223' AND a.nombre = 'Yuval Noah Harari')
    OR (l.isbn = '9788401352836' AND a.nombre = 'Isabel Allende')
ON CONFLICT DO NOTHING;

INSERT INTO libro_categorias (libro_id, categoria_id)
SELECT l.id, c.id
  FROM libros l, categorias c
 WHERE (l.isbn = '9780132350884' AND c.nombre = 'Tecnología')
    OR (l.isbn = '9780134757599' AND c.nombre = 'Tecnología')
    OR (l.isbn = '9780307474728' AND c.nombre = 'Ficción')
    OR (l.isbn = '9788499926223' AND c.nombre = 'Historia')
    OR (l.isbn = '9788401352836' AND c.nombre = 'Ficción')
ON CONFLICT DO NOTHING;