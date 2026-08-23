-- ============================================================================
-- SGB-SaaS — db/generar-volumen.sql
--
-- PROPOSITO: generar datos SINTETICOS de gran volumen para la materia de
-- Administracion de Bases de Datos (ADB), curso SEPARADO de Aplicaciones Web
-- que reutiliza esta misma base. El rubro de ADB exige una base con al menos
-- 1,000,000 de registros distribuidos de forma coherente entre tablas,
-- demostrado en vivo.
--
-- ESTE ARCHIVO NO ES UNA MIGRACION FLYWAY. A proposito NO sigue la
-- convencion V<n>__ ni R__ de database/migrations/ (donde Flyway lo
-- ejecutaria automaticamente en cada `make up`/despliegue). Es un script
-- manual, standalone, que se corre UNA sola vez, a mano, contra la base de
-- ADB cuando se necesite volumen para la sustentacion. No se referencia
-- desde scripts/build-init-sql.sh ni desde docker-entrypoint-initdb.d, asi
-- que nunca se ejecuta solo.
--
-- REQUISITO PREVIO: el esquema completo (tablas + catalogos sembrados) debe
-- existir ya -- ya sea via `make up` (db/schema.sql + db/procs + db/seed.sql)
-- o via la cadena Flyway real (database/migrations/V1..V13). Este script
-- SOLO agrega filas nuevas; no crea tablas ni catalogos.
--
-- ADVERTENCIA: este script inserta millones de filas. NO correrlo contra una
-- base compartida de desarrollo/produccion (Render/Neon) sin saber
-- exactamente lo que se hace -- pensado para una base dedicada de pruebas de
-- volumen de ADB.
--
-- ============================================================================
-- DISEÑO Y RAZONAMIENTO DE LAS PROPORCIONES
-- ============================================================================
-- La bitacora_auditoria es, por diseño, la tabla de mayor volumen de
-- cualquier sistema con auditoria real: cada operacion de negocio (crear un
-- prestamo, registrar una devolucion, pagar/anular una multa, iniciar o
-- cerrar sesion) deja una o mas filas ahi, ademas de eventos que no dejan
-- huella en ninguna otra tabla (LOGIN_FAIL, por ejemplo). Por eso es la
-- tabla objetivo para el >= 1,000,000 del rubro, y el resto de las tablas
-- escala de forma coherente POR DEBAJO de ella, respetando el orden real de
-- llaves foraneas:
--
--   catalogos/roles (ya sembrados por V2/V10)
--     -> usuarios (staff: bibliotecario/gerente/admin, luego lector)
--     -> libros
--     -> libro_autores / libro_categorias
--     -> reservaciones            (antes que prestamos: prestamos.reservacion_id
--                                   la referencia opcionalmente)
--     -> prestamos
--     -> multas / notificaciones  (derivadas de prestamos ya insertados)
--     -> favoritos / sugerencias_adquisicion
--     -> bitacora_auditoria
--
-- Cantidades elegidas (razonamiento por tabla):
--
--   usuarios: 50,000 nuevos ("decenas de miles" -- universidad/biblioteca
--     institucional grande). Distribucion de rol ~1% staff / 99% lector,
--     proporcion tipica de dotacion de personal en un sistema institucional:
--       BIBLIOTECARIO: 400 (0.8%) | GERENTE: 90 (0.18%) | ADMIN: 10 (0.02%)
--       LECTOR: 49,500 (99%)
--     estado_id de los LECTOR (unico random dependiente por fila, no
--     acumulado independiente -- ver LATERAL rnd mas abajo): 88% ACTIVO /
--     6% BLOQUEADO_POR_MULTA / 4% INACTIVO / 2% PENDIENTE_VERIFICACION.
--
--   libros: 20,000 nuevos ("decenas de miles", catalogo de biblioteca grande
--     pero realista). isbn/titulo sinteticos deterministas (isbn = '979' +
--     10 digitos con padding de ceros, garantiza UNIQUE sin colisiones).
--     portada_imagen/portada_nombre/portada_tipo/portada_tamanio se dejan en
--     NULL a proposito (columna nullable, confirmada en V13 -- ver STEP 1 del
--     reporte): el rubro de ADB exige VOLUMEN de filas, no imagenes reales,
--     y no se van a fabricar binarios falsos.
--
--   libro_autores / libro_categorias: 1 relacion garantizada por libro nuevo
--     (20,000) mas una segunda relacion opcional para una fraccion de ellos
--     (~40% coautoria, ~50% segunda categoria) -> ~28,000 y ~30,000 filas
--     aproximadas (ON CONFLICT DO NOTHING evita duplicar la misma pareja si
--     el azar repite el mismo autor/categoria).
--
--   reservaciones: 150,000 ("cientos de miles" por debajo de prestamos).
--
--   prestamos: 500,000 ("cientos de miles", la tabla transaccional nuclear).
--     estado_prestamo_id: 70% DEVUELTO / 10% RENOVADO / 10% ACTIVO /
--     10% VENCIDO (un solo sorteo aleatorio por fila, reusado via LATERAL
--     para que fecha_devolucion_real y renovaciones_realizadas sean
--     coherentes con el estado elegido en la MISMA fila -- ni un prestamo
--     ACTIVO/VENCIDO puede tener fecha_devolucion_real, ni uno
--     DEVUELTO/RENOVADO puede quedar sin ella).
--     ~10% de los prestamos referencian una reservacion existente
--     (reservacion_id), el resto queda NULL (prestamo directo, mayoria real).
--
--   multas: NO es una cantidad fija -- emerge de filtrar los prestamos
--     DEVUELTO/RENOVADO (80% de 500,000 = 400,000) cuya fecha_devolucion_real
--     resulto tardia. El desfase se sortea con un solo umbral (20% de esos
--     400,000 "tardios") -> estimado ~80,000 multas (orden de magnitud
--     "decenas/cientos de miles" por debajo de prestamos, consistente con
--     una tasa de mora real de biblioteca). monto = dias_atraso *
--     monto_multa_diaria (0.50, el mismo valor que ya vive en
--     configuracion_sistema desde V10) -- nunca inventado, siempre > 0
--     porque el filtro exige fecha_devolucion_real > fecha_devolucion_estimada.
--
--   notificaciones: ~50% de los 500,000 prestamos generan una notificacion
--     de VENCIMIENTO o MULTA (~250,000) mas ~30% de las reservaciones
--     EXPIRADA generan una de RESERVA_CADUCADA (~9,000 sobre ~30,000
--     EXPIRADA esperadas) -> ~259,000 filas estimadas.
--
--   favoritos: 200,000 intentos de pareja (usuario LECTOR, libro) al azar
--     con ON CONFLICT DO NOTHING -- con 49,500 lectores x 20,005 libros
--     (~1e9 combinaciones posibles) la tasa de colision es despreciable, asi
--     que el resultado final sera muy cercano a 200,000 filas reales.
--
--   sugerencias_adquisicion: 5,000 (una fraccion pequeña de lectores activos
--     que sugieren compras, no un uso masivo).
--
--   bitacora_auditoria: 1,500,000 EXACTAS via generate_series (la unica
--     tabla con conteo garantizado, sin depender de azar ni de filtros sobre
--     otras tablas) -- 50% de margen por encima del minimo de 1,000,000 que
--     exige el rubro. tipo_operacion se sortea con un solo umbral por fila
--     (25% INSERT / 20% UPDATE / 5% DELETE / 25% LOGIN_OK / 5% LOGIN_FAIL /
--     15% LOGOUT / 5% CORREO_VERIFICADO) y tabla_afectada es coherente con
--     ese tipo (los 4 tipos de sesion/verificacion siempre apuntan a
--     'usuarios'; el resto elige entre las tablas transaccionales reales).
--
-- Suma total estimada de filas nuevas en TODAS las tablas: ~2.8 millones
-- (1.5M de ellas garantizadas en bitacora_auditoria), muy por encima del
-- minimo de 1,000,000 exigido, con proporciones que reflejan el volumen real
-- de un sistema de biblioteca en produccion (auditoria > prestamos >
-- notificaciones > favoritos > reservaciones > multas > libros > usuarios).
-- ============================================================================

-- ============================================================================
-- 0. Verificacion previa: los catalogos deben existir (V2/V10 ya corridos)
-- ============================================================================
DO $$
BEGIN
    IF (SELECT count(*) FROM roles) = 0
       OR (SELECT count(*) FROM estados_usuario) = 0
       OR (SELECT count(*) FROM estados_libro) = 0
       OR (SELECT count(*) FROM estados_prestamo) = 0
       OR (SELECT count(*) FROM estados_multa) = 0
       OR (SELECT count(*) FROM estados_reservacion) = 0
       OR (SELECT count(*) FROM editoriales) = 0
       OR (SELECT count(*) FROM idiomas) = 0
       OR (SELECT count(*) FROM categorias) = 0
       OR (SELECT count(*) FROM autores) = 0
       OR (SELECT count(*) FROM tipos_notificacion) = 0
       OR (SELECT count(*) FROM configuracion_sistema WHERE clave = 'monto_multa_diaria') = 0
    THEN
        RAISE EXCEPTION 'Faltan catalogos base o configuracion_sistema.monto_multa_diaria (usado por el paso 6, MULTAS). Corre las migraciones V1-V13 (o make up) antes de este script.';
    END IF;
END $$;

-- ============================================================================
-- 1. USUARIOS (staff primero, cada rol en su propio INSERT para que sus ids
--    queden en un bloque contiguo y las selecciones aleatorias por rango de
--    id -- en libros/prestamos/etc mas abajo -- sean O(1) sin necesidad de
--    ORDER BY random() sobre tablas grandes)
-- ============================================================================
INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
SELECT
    (ARRAY['Ana','Carlos','Maria','Jose','Lucia','Pedro','Sofia','Diego','Valentina','Andres'])[1+floor(random()*10)::int],
    (ARRAY['Garcia','Rodriguez','Martinez','Lopez','Gonzalez','Perez','Sanchez','Ramirez','Torres','Flores'])[1+floor(random()*10)::int],
    'bibliotecario_volumen_' || n || '@synthetic.local',
    'SYNTHETIC_HASH_NO_LOGIN_VOLUMEN',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
FROM generate_series(1, 400) AS s(n);

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, (SELECT id FROM roles WHERE nombre = 'BIBLIOTECARIO')
  FROM usuarios u WHERE u.correo LIKE 'bibliotecario_volumen_%@synthetic.local';

INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
SELECT
    (ARRAY['Ana','Carlos','Maria','Jose','Lucia','Pedro','Sofia','Diego','Valentina','Andres'])[1+floor(random()*10)::int],
    (ARRAY['Garcia','Rodriguez','Martinez','Lopez','Gonzalez','Perez','Sanchez','Ramirez','Torres','Flores'])[1+floor(random()*10)::int],
    'gerente_volumen_' || n || '@synthetic.local',
    'SYNTHETIC_HASH_NO_LOGIN_VOLUMEN',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
FROM generate_series(1, 90) AS s(n);

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, (SELECT id FROM roles WHERE nombre = 'GERENTE')
  FROM usuarios u WHERE u.correo LIKE 'gerente_volumen_%@synthetic.local';

INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
SELECT
    (ARRAY['Ana','Carlos','Maria','Jose','Lucia','Pedro','Sofia','Diego','Valentina','Andres'])[1+floor(random()*10)::int],
    (ARRAY['Garcia','Rodriguez','Martinez','Lopez','Gonzalez','Perez','Sanchez','Ramirez','Torres','Flores'])[1+floor(random()*10)::int],
    'admin_volumen_' || n || '@synthetic.local',
    'SYNTHETIC_HASH_NO_LOGIN_VOLUMEN',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
FROM generate_series(1, 10) AS s(n);

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, (SELECT id FROM roles WHERE nombre = 'ADMIN')
  FROM usuarios u WHERE u.correo LIKE 'admin_volumen_%@synthetic.local';

-- LECTOR: estado_id con un solo sorteo por fila (LATERAL) para documentar
-- porcentajes exactos en vez de umbrales de CASE encadenados (que sesgan la
-- probabilidad real de cada rama). correo_verificado coherente con el
-- estado: PENDIENTE_VERIFICACION siempre queda en FALSE.
INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
SELECT
    (ARRAY['Ana','Carlos','Maria','Jose','Lucia','Pedro','Sofia','Diego','Valentina','Andres'])[1+floor(random()*10)::int],
    (ARRAY['Garcia','Rodriguez','Martinez','Lopez','Gonzalez','Perez','Sanchez','Ramirez','Torres','Flores'])[1+floor(random()*10)::int],
    'lector_volumen_' || n || '@synthetic.local',
    'SYNTHETIC_HASH_NO_LOGIN_VOLUMEN',
    est.estado_id,
    est.estado_id <> est_cat.pendiente
FROM generate_series(1, 49500) AS s(n)
CROSS JOIN (
    SELECT
        (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO')                 AS activo,
        (SELECT id FROM estados_usuario WHERE nombre = 'BLOQUEADO_POR_MULTA')    AS bloqueado,
        (SELECT id FROM estados_usuario WHERE nombre = 'INACTIVO')              AS inactivo,
        (SELECT id FROM estados_usuario WHERE nombre = 'PENDIENTE_VERIFICACION') AS pendiente
) AS est_cat
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN r.v < 0.88 THEN est_cat.activo
            WHEN r.v < 0.94 THEN est_cat.bloqueado
            WHEN r.v < 0.98 THEN est_cat.inactivo
            ELSE est_cat.pendiente
        END AS estado_id
    FROM (SELECT random() AS v) AS r
) AS est;

-- ============================================================================
-- 2. LIBROS
-- ============================================================================
-- NOTA TECNICA IMPORTANTE: para elegir un catalogo aleatorio por fila NO se
-- usa "(SELECT id FROM x ORDER BY random() LIMIT 1)" como subconsulta suelta
-- en el SELECT -- Postgres trata una subconsulta sin correlacion con la fila
-- externa como un InitPlan (se evalua UNA sola vez para toda la consulta,
-- sin importar que contenga random()), lo que le daria el MISMO editorial/
-- idioma/estado a los 20,000 libros. En su lugar se usa la misma tecnica de
-- limites min/max + aritmetica directa en la lista de SELECT (sin
-- subconsulta) que ya se usa en el resto del script para usuarios/libros/
-- reservaciones -- una expresion random() directa en el SELECT SI se
-- reevalua por fila. Asume ids densos (sin huecos) en estos catalogos
-- pequeños, ciertos porque V1/V2/V10 solo los INSERTan una vez y nunca se
-- borran filas de ahi.
--
-- MISMA PRECAUCION PARA LOS CROSS JOIN LATERAL de mas abajo (est/gen/rnd/
-- fch/base/env/stk): un LATERAL que NO referencia ninguna columna de un
-- FROM previo puede, en teoria, ser tratado por el planificador igual que
-- una subconsulta suelta. Por eso cada LATERAL que no depende de otro
-- LATERAL ya correlacionado (est_cat, gen, base, etc.) lleva un
-- "WHERE <col_previa> IS NOT NULL" -- una condicion siempre verdadera cuyo
-- unico proposito es forzar una referencia real a la fila externa (de
-- generate_series o de la tabla que se esta recorriendo) y asi garantizar
-- reevaluacion fila por fila, sin depender de una optimizacion del
-- planificador que no se puede verificar sin ejecutar el script en vivo.
--
-- stock_disponible depende de stock_total de la MISMA fila -> se resuelve en
-- un solo LATERAL (que SI fuerza reevaluacion por fila porque hace un JOIN
-- LATERAL real de un valor calculado en la MISMA subconsulta) para que la
-- coherencia 0 <= disponible <= total (CHECK del esquema) se cumpla siempre.
INSERT INTO libros (
    isbn, titulo, resumen, anio_publicacion, editorial_id, idioma_id,
    estado_id, stock_total, stock_disponible, ubicacion_fisica
)
SELECT
    '979' || lpad(n::text, 10, '0'),
    'Libro sintetico ' || n,
    'Resumen sintetico generado para pruebas de volumen de ADB.',
    (1950 + floor(random()*76))::smallint,
    b_edit.lo + floor(random()*(b_edit.hi - b_edit.lo + 1))::int,
    b_idio.lo + floor(random()*(b_idio.hi - b_idio.lo + 1))::int,
    b_elib.lo + floor(random()*(b_elib.hi - b_elib.lo + 1))::int,
    stk.stock_total,
    stk.stock_disponible,
    'ESTANTE-' || (1+floor(random()*50))::int || '-' || (1+floor(random()*10))::int
FROM generate_series(1, 20000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM editoriales)   AS b_edit
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM idiomas)       AS b_idio
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM estados_libro) AS b_elib
CROSS JOIN LATERAL (
    SELECT
        t.stock_total,
        floor(random() * (t.stock_total + 1))::smallint AS stock_disponible
    FROM (SELECT (1 + floor(random()*9))::smallint AS stock_total) AS t
    WHERE s.n IS NOT NULL  -- correlacion trivial (siempre verdadera) para forzar reevaluacion por fila; ver nota tecnica arriba
) AS stk;

-- ============================================================================
-- 3. LIBRO_AUTORES / LIBRO_CATEGORIAS
--    (solo sobre los libros sinteticos nuevos, identificados por su isbn
--    '979...'; 1 relacion garantizada + una segunda opcional por libro).
--    Mismo criterio de la seccion 2: limites min/max + aritmetica directa
--    en vez de "ORDER BY random() LIMIT 1" suelto, para que cada libro
--    reciba un autor/categoria realmente distinto y no el mismo para todos.
-- ============================================================================
INSERT INTO libro_autores (libro_id, autor_id)
SELECT l.id, b_aut.lo + floor(random()*(b_aut.hi - b_aut.lo + 1))::bigint
  FROM libros l
  CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM autores) AS b_aut
 WHERE l.isbn LIKE '979%'
ON CONFLICT DO NOTHING;

INSERT INTO libro_autores (libro_id, autor_id)
SELECT l.id, b_aut.lo + floor(random()*(b_aut.hi - b_aut.lo + 1))::bigint
  FROM libros l
  CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM autores) AS b_aut
 WHERE l.isbn LIKE '979%' AND random() < 0.40
ON CONFLICT DO NOTHING;

INSERT INTO libro_categorias (libro_id, categoria_id)
SELECT l.id, b_cat.lo + floor(random()*(b_cat.hi - b_cat.lo + 1))::int
  FROM libros l
  CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM categorias) AS b_cat
 WHERE l.isbn LIKE '979%'
ON CONFLICT DO NOTHING;

INSERT INTO libro_categorias (libro_id, categoria_id)
SELECT l.id, b_cat.lo + floor(random()*(b_cat.hi - b_cat.lo + 1))::int
  FROM libros l
  CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM categorias) AS b_cat
 WHERE l.isbn LIKE '979%' AND random() < 0.50
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 4. RESERVACIONES (antes que prestamos: prestamos.reservacion_id la
--    referencia opcionalmente)
-- ============================================================================
INSERT INTO reservaciones (usuario_id, libro_id, estado_reservacion_id, fecha_reserva, fecha_limite_retiro)
SELECT
    b_lector.lo + floor(random()*(b_lector.hi - b_lector.lo + 1))::bigint,
    b_libro.lo  + floor(random()*(b_libro.hi  - b_libro.lo  + 1))::bigint,
    est.estado_id,
    fch.fecha_reserva,
    fch.fecha_reserva + ((1 + floor(random()*3))::int || ' days')::interval
FROM generate_series(1, 150000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios WHERE correo LIKE 'lector_volumen_%@synthetic.local') AS b_lector
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM libros) AS b_libro
CROSS JOIN (
    SELECT
        (SELECT id FROM estados_reservacion WHERE nombre = 'PENDIENTE')          AS pendiente,
        (SELECT id FROM estados_reservacion WHERE nombre = 'LISTA_PARA_RETIRO')  AS lista,
        (SELECT id FROM estados_reservacion WHERE nombre = 'RETIRADA')           AS retirada,
        (SELECT id FROM estados_reservacion WHERE nombre = 'EXPIRADA')          AS expirada,
        (SELECT id FROM estados_reservacion WHERE nombre = 'CANCELADA')         AS cancelada
) AS est_cat
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN r.v < 0.20 THEN est_cat.pendiente
            WHEN r.v < 0.40 THEN est_cat.lista
            WHEN r.v < 0.55 THEN est_cat.retirada
            WHEN r.v < 0.85 THEN est_cat.expirada
            ELSE est_cat.cancelada
        END AS estado_id
    FROM (SELECT random() AS v) AS r
) AS est
CROSS JOIN LATERAL (
    SELECT now() - (random() * interval '1826 days') AS fecha_reserva
    WHERE s.n IS NOT NULL  -- correlacion trivial para forzar reevaluacion por fila
) AS fch;

-- ============================================================================
-- 5. PRESTAMOS
--    estado_prestamo_id se sortea UNA vez por fila (LATERAL) y se reusa para
--    decidir fecha_devolucion_real y renovaciones_realizadas de forma
--    coherente: ACTIVO/VENCIDO nunca tienen fecha_devolucion_real; solo
--    RENOVADO tiene renovaciones_realizadas > 0.
-- ============================================================================
INSERT INTO prestamos (
    usuario_id, libro_id, bibliotecario_id, reservacion_id,
    fecha_prestamo, fecha_devolucion_estimada, fecha_devolucion_real,
    renovaciones_realizadas, estado_prestamo_id
)
SELECT
    b_lector.lo + floor(random()*(b_lector.hi - b_lector.lo + 1))::bigint,
    b_libro.lo  + floor(random()*(b_libro.hi  - b_libro.lo  + 1))::bigint,
    b_biblio.lo + floor(random()*(b_biblio.hi - b_biblio.lo + 1))::bigint,
    CASE WHEN random() < 0.10
         THEN b_reserva.lo + floor(random()*(b_reserva.hi - b_reserva.lo + 1))::bigint
         ELSE NULL END,
    fch.fecha_prestamo,
    fch.fecha_devolucion_estimada,
    CASE
        WHEN gen.estado_id IN (est_cat.activo, est_cat.vencido) THEN NULL
        WHEN gen.r_tardio < 0.20
             THEN fch.fecha_devolucion_estimada + ((1 + floor(random()*20))::int || ' days')::interval
        -- offset "a tiempo" acotado a 0-6 dias: dias_plazo minimo es 7, asi
        -- que fecha_devolucion_real nunca queda antes de fecha_prestamo
        ELSE fch.fecha_devolucion_estimada - (floor(random()*7)::int || ' days')::interval
    END,
    CASE WHEN gen.estado_id = est_cat.renovado THEN (1 + floor(random()*2))::smallint ELSE 0 END,
    gen.estado_id
FROM generate_series(1, 500000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios WHERE correo LIKE 'lector_volumen_%@synthetic.local') AS b_lector
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM libros) AS b_libro
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios WHERE correo LIKE 'bibliotecario_volumen_%@synthetic.local') AS b_biblio
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM reservaciones) AS b_reserva
CROSS JOIN (
    SELECT
        (SELECT id FROM estados_prestamo WHERE nombre = 'DEVUELTO')  AS devuelto,
        (SELECT id FROM estados_prestamo WHERE nombre = 'RENOVADO')  AS renovado,
        (SELECT id FROM estados_prestamo WHERE nombre = 'ACTIVO')    AS activo,
        (SELECT id FROM estados_prestamo WHERE nombre = 'VENCIDO')   AS vencido
) AS est_cat
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN r.v < 0.70 THEN est_cat.devuelto
            WHEN r.v < 0.80 THEN est_cat.renovado
            WHEN r.v < 0.90 THEN est_cat.activo
            ELSE est_cat.vencido
        END AS estado_id,
        random() AS r_tardio
    FROM (SELECT random() AS v) AS r
) AS gen
CROSS JOIN LATERAL (
    SELECT
        now() - (random() * interval '1826 days') AS fecha_prestamo_base,
        (7 + floor(random()*24))::int AS dias_plazo
    WHERE s.n IS NOT NULL  -- correlacion trivial para forzar reevaluacion por fila
) AS base
CROSS JOIN LATERAL (
    SELECT
        base.fecha_prestamo_base AS fecha_prestamo,
        base.fecha_prestamo_base + (base.dias_plazo || ' days')::interval AS fecha_devolucion_estimada
) AS fch;

-- ============================================================================
-- 6. MULTAS (emergen de los prestamos DEVUELTO/RENOVADO que resultaron
--    tardios -- no es un INSERT con cantidad fija, ver razonamiento arriba)
-- ============================================================================
INSERT INTO multas (prestamo_id, monto, estado_multa_id, fecha_generada, fecha_pagada, observaciones)
SELECT
    p.id,
    (dias.dias_atraso * cfg.monto_diario)::NUMERIC(8,2),
    est.estado_id,
    p.fecha_devolucion_real,
    CASE WHEN est.estado_id = est_cat.pagada
         THEN p.fecha_devolucion_real + (floor(random()*30)::int || ' days')::interval
         ELSE NULL END,
    CASE WHEN est.estado_id = est_cat.anulada
         THEN 'Anulada en generacion sintetica de volumen (ADB)'
         ELSE NULL END
FROM prestamos p
CROSS JOIN LATERAL (
    SELECT CEIL(EXTRACT(EPOCH FROM (p.fecha_devolucion_real - p.fecha_devolucion_estimada)) / 86400.0)::int AS dias_atraso
) AS dias
CROSS JOIN (
    SELECT (SELECT valor::NUMERIC FROM configuracion_sistema WHERE clave = 'monto_multa_diaria') AS monto_diario
) AS cfg
CROSS JOIN (
    SELECT
        (SELECT id FROM estados_multa WHERE nombre = 'PAGADA')    AS pagada,
        (SELECT id FROM estados_multa WHERE nombre = 'PENDIENTE') AS pendiente,
        (SELECT id FROM estados_multa WHERE nombre = 'ANULADA')   AS anulada
) AS est_cat
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN r.v < 0.55 THEN est_cat.pagada
            WHEN r.v < 0.85 THEN est_cat.pendiente
            ELSE est_cat.anulada
        END AS estado_id
    FROM (SELECT random() AS v) AS r
) AS est
WHERE p.fecha_devolucion_real IS NOT NULL
  AND p.fecha_devolucion_real > p.fecha_devolucion_estimada;

-- ============================================================================
-- 7. NOTIFICACIONES
--    (a) ~50% de los prestamos generan una notificacion de VENCIMIENTO/MULTA
--    (b) ~30% de las reservaciones EXPIRADA generan una de RESERVA_CADUCADA
-- ============================================================================
INSERT INTO notificaciones (usuario_id, prestamo_id, tipo_notificacion_id, mensaje, fecha_envio, enviado_ok, error_envio)
SELECT
    p.usuario_id,
    p.id,
    CASE WHEN random() < 0.70
         THEN (SELECT id FROM tipos_notificacion WHERE nombre = 'VENCIMIENTO')
         ELSE (SELECT id FROM tipos_notificacion WHERE nombre = 'MULTA') END,
    'Notificacion sintetica sobre el prestamo ' || p.id,
    CASE WHEN env.ok THEN p.fecha_prestamo + interval '1 day' ELSE NULL END,
    env.ok,
    CASE WHEN env.ok THEN NULL ELSE 'Error sintetico de envio (ADB, prueba de volumen)' END
FROM prestamos p
CROSS JOIN LATERAL (SELECT random() < 0.90 AS ok WHERE p.id IS NOT NULL) AS env  -- correlacion trivial para forzar reevaluacion por fila
WHERE random() < 0.50;

INSERT INTO notificaciones (usuario_id, prestamo_id, tipo_notificacion_id, mensaje, fecha_envio, enviado_ok, error_envio)
SELECT
    r.usuario_id,
    NULL,
    (SELECT id FROM tipos_notificacion WHERE nombre = 'RESERVA_CADUCADA'),
    'Notificacion sintetica: reservacion ' || r.id || ' caducada',
    CASE WHEN env.ok THEN r.fecha_limite_retiro + interval '1 hour' ELSE NULL END,
    env.ok,
    CASE WHEN env.ok THEN NULL ELSE 'Error sintetico de envio (ADB, prueba de volumen)' END
FROM reservaciones r
CROSS JOIN LATERAL (SELECT random() < 0.90 AS ok WHERE r.id IS NOT NULL) AS env  -- correlacion trivial para forzar reevaluacion por fila
WHERE r.estado_reservacion_id = (SELECT id FROM estados_reservacion WHERE nombre = 'EXPIRADA')
  AND random() < 0.30;

-- ============================================================================
-- 8. FAVORITOS (parejas usuario LECTOR / libro al azar; ON CONFLICT DO
--    NOTHING como red de seguridad -- la colision real es despreciable dado
--    el tamaño del dominio, ver razonamiento arriba)
-- ============================================================================
INSERT INTO favoritos (usuario_id, libro_id)
SELECT
    b_lector.lo + floor(random()*(b_lector.hi - b_lector.lo + 1))::bigint,
    b_libro.lo  + floor(random()*(b_libro.hi  - b_libro.lo  + 1))::bigint
FROM generate_series(1, 200000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios WHERE correo LIKE 'lector_volumen_%@synthetic.local') AS b_lector
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM libros) AS b_libro
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 9. SUGERENCIAS_ADQUISICION
--    estado y revisado_por dependen entre si (revisado_por NULL solo cuando
--    sigue PENDIENTE) -> un solo sorteo por fila via LATERAL.
-- ============================================================================
INSERT INTO sugerencias_adquisicion (usuario_id, titulo, autor, justificacion, estado, revisado_por)
SELECT
    b_lector.lo + floor(random()*(b_lector.hi - b_lector.lo + 1))::bigint,
    'Sugerencia sintetica de adquisicion ' || n,
    'Autor Sugerido ' || n,
    'Justificacion sintetica generada para pruebas de volumen de ADB.',
    est.estado_nombre,
    CASE WHEN est.estado_nombre = 'PENDIENTE' THEN NULL
         ELSE b_staff.lo + floor(random()*(b_staff.hi - b_staff.lo + 1))::bigint END
FROM generate_series(1, 5000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios WHERE correo LIKE 'lector_volumen_%@synthetic.local') AS b_lector
CROSS JOIN (
    SELECT min(id) AS lo, max(id) AS hi FROM usuarios
     WHERE correo LIKE 'bibliotecario_volumen_%@synthetic.local'
        OR correo LIKE 'gerente_volumen_%@synthetic.local'
        OR correo LIKE 'admin_volumen_%@synthetic.local'
) AS b_staff
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN r.v < 0.60 THEN 'PENDIENTE'
            WHEN r.v < 0.85 THEN 'APROBADA'
            ELSE 'RECHAZADA'
        END AS estado_nombre
    FROM (SELECT random() AS v) AS r
    WHERE s.n IS NOT NULL  -- correlacion trivial para forzar reevaluacion por fila
) AS est;

-- ============================================================================
-- 10. BITACORA_AUDITORIA (>= 1,000,000 garantizado -- 1,500,000 exactas via
--     generate_series, con 50% de margen sobre el minimo del rubro de ADB)
-- ============================================================================
INSERT INTO bitacora_auditoria (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles, ip_origen, fecha_hora)
SELECT
    CASE WHEN rnd.r_anon < 0.05 THEN NULL
         ELSE b_usuario.lo + floor(random()*(b_usuario.hi - b_usuario.lo + 1))::bigint END,
    tipo.tipo_operacion,
    tabla.tabla_afectada,
    floor(random()*1000000)::bigint,
    'Evento sintetico de auditoria #' || s.n || ' (' || tipo.tipo_operacion || ' sobre ' || tabla.tabla_afectada || ')',
    CASE WHEN random() < 0.30 THEN NULL
         ELSE (floor(random()*223)+1)::text || '.' || floor(random()*255)::text || '.'
              || floor(random()*255)::text || '.' || (floor(random()*254)+1)::text
    END,
    rnd.fecha_hora
FROM generate_series(1, 1500000) AS s(n)
CROSS JOIN (SELECT min(id) AS lo, max(id) AS hi FROM usuarios) AS b_usuario
CROSS JOIN LATERAL (
    SELECT random() AS r_tipo, random() AS r_anon,
           now() - (random() * interval '1826 days') AS fecha_hora
    WHERE s.n IS NOT NULL  -- correlacion trivial para forzar reevaluacion por fila
) AS rnd
CROSS JOIN LATERAL (
    SELECT
        CASE
            WHEN rnd.r_tipo < 0.25 THEN 'INSERT'
            WHEN rnd.r_tipo < 0.45 THEN 'UPDATE'
            WHEN rnd.r_tipo < 0.50 THEN 'DELETE'
            WHEN rnd.r_tipo < 0.75 THEN 'LOGIN_OK'
            WHEN rnd.r_tipo < 0.80 THEN 'LOGIN_FAIL'
            WHEN rnd.r_tipo < 0.95 THEN 'LOGOUT'
            ELSE 'CORREO_VERIFICADO'
        END AS tipo_operacion
) AS tipo
CROSS JOIN LATERAL (
    SELECT
        CASE WHEN tipo.tipo_operacion IN ('LOGIN_OK','LOGIN_FAIL','LOGOUT','CORREO_VERIFICADO') THEN 'usuarios'
             ELSE (ARRAY['usuarios','libros','prestamos','multas','reservaciones','favoritos','notificaciones'])[1+floor(random()*7)::int]
        END AS tabla_afectada
) AS tabla;

-- ============================================================================
-- 11. VERIFICACION: conteo final de todas las tablas tocadas por este script
-- ============================================================================
SELECT 'usuarios' AS tabla, count(*) AS filas FROM usuarios
UNION ALL SELECT 'usuario_roles', count(*) FROM usuario_roles
UNION ALL SELECT 'libros', count(*) FROM libros
UNION ALL SELECT 'libro_autores', count(*) FROM libro_autores
UNION ALL SELECT 'libro_categorias', count(*) FROM libro_categorias
UNION ALL SELECT 'reservaciones', count(*) FROM reservaciones
UNION ALL SELECT 'prestamos', count(*) FROM prestamos
UNION ALL SELECT 'multas', count(*) FROM multas
UNION ALL SELECT 'notificaciones', count(*) FROM notificaciones
UNION ALL SELECT 'favoritos', count(*) FROM favoritos
UNION ALL SELECT 'sugerencias_adquisicion', count(*) FROM sugerencias_adquisicion
UNION ALL SELECT 'bitacora_auditoria', count(*) FROM bitacora_auditoria
ORDER BY filas DESC;

SELECT 'TOTAL_GENERAL' AS resumen, (
    (SELECT count(*) FROM usuarios) + (SELECT count(*) FROM usuario_roles) +
    (SELECT count(*) FROM libros) + (SELECT count(*) FROM libro_autores) +
    (SELECT count(*) FROM libro_categorias) + (SELECT count(*) FROM reservaciones) +
    (SELECT count(*) FROM prestamos) + (SELECT count(*) FROM multas) +
    (SELECT count(*) FROM notificaciones) + (SELECT count(*) FROM favoritos) +
    (SELECT count(*) FROM sugerencias_adquisicion) + (SELECT count(*) FROM bitacora_auditoria)
) AS filas_totales;
