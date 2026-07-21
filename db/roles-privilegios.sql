-- ============================================================================
-- PROPUESTA DE ADMINISTRACIÓN DE USUARIOS, ROLES Y PRIVILEGIOS
-- Proyecto: SGB-SaaS (Sistema de Gestión Bibliotecaria Web)
-- Materia: Administración de Bases de Datos
-- Motor: PostgreSQL 16
-- Base: schema_completo_24_tablas.sql (mismo repositorio de Aplicaciones Web,
--       usado aquí solo para trabajar administración de BD; sin entregables
--       de código/despliegue en esta materia)
-- ============================================================================
-- IMPORTANTE: no confundir con `roles` / `permisos` (tablas 6 y 7 del schema),
-- que son RBAC de la APLICACIÓN (lógica de negocio, gestionada por el backend
-- Spring Boot). Aquí definimos roles de POSTGRESQL: quién se conecta a la BD
-- y qué puede hacer dentro de ella. Ambos niveles son independientes mostrando
-- el mismo patrón "defensa en profundidad" (una capa protege aunque otra falle).
-- ============================================================================
-- ESTADO: archivo teórico/de entrega para Administración de BD. NO se ha
-- ejecutado contra ningún PostgreSQL real (ni el de desarrollo de
-- Aplicaciones Web, que hoy usa una sola credencial `sgb_user` para todo el
-- backend). Aplicarlo implica migrar a una arquitectura multi-datasource en
-- el backend (una credencial de conexión por rol de aplicación) — fuera de
-- alcance hasta que se decida esa migración.
-- ============================================================================


-- ============================================================================
-- 1. IDENTIFICACIÓN DE USUARIOS DEL SISTEMA
-- ============================================================================
-- Flujo real de la aplicación (según alcance funcional):
--   Landing pública -> Catálogo (ver disponibilidad, sin poder actuar) ->
--   Login -> según rol asignado en usuario_roles:
--     LECTOR:        favoritos, reservas, préstamos propios, sugerencias
--     BIBLIOTECARIO: préstamos/devoluciones, catálogo, multas, reservaciones
--     GERENTE:       todo lo anterior + cuentas de personal + reportes
--     ADMIN:         administración técnica de la plataforma
--
-- Usuarios de PostgreSQL (conexiones reales del backend, una por rol de app,
-- más 2 cuentas humanas de infraestructura):
--
--  a) admin_dba          -> Tech Lead / DBA (Marlon). Acceso directo.
--  b) app_catalogo       -> conexión del backend para el VISITANTE ANÓNIMO
--                           (landing + catálogo público, sin login).
--  c) app_lector         -> conexión del backend para un LECTOR autenticado.
--  d) app_bibliotecario  -> conexión del backend para un BIBLIOTECARIO.
--  e) app_gerente        -> conexión del backend para un GERENTE.
--  f) app_admin          -> conexión del backend para el rol técnico ADMIN
--                           (gestión de catálogos maestros y configuración).
--  g) auditor_readonly   -> auditor / coordinador académico externo.
--
-- El backend mantiene un pool de conexión por rol de aplicación y selecciona
-- la credencial de BD correcta según el rol vigente del usuario autenticado
-- (tabla usuario_roles), ya validado en el JWT.
-- ============================================================================


-- ============================================================================
-- 2. CREACIÓN DE USUARIOS DE POSTGRESQL (LOGIN)
-- ============================================================================
CREATE ROLE admin_dba         LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_1' VALID UNTIL 'infinity';
CREATE ROLE app_catalogo      LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_2';
CREATE ROLE app_lector        LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_3';
CREATE ROLE app_bibliotecario LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_4';
CREATE ROLE app_gerente       LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_5';
CREATE ROLE app_admin         LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_6';
CREATE ROLE auditor_readonly  LOGIN PASSWORD 'CAMBIAR_EN_PRODUCCION_7';

-- Límite de conexiones simultáneas por credencial (evita que un bug agote
-- el pool de PostgreSQL completo):
ALTER ROLE app_catalogo      CONNECTION LIMIT 80;  -- tráfico público, el más alto
ALTER ROLE app_lector        CONNECTION LIMIT 50;
ALTER ROLE app_bibliotecario CONNECTION LIMIT 20;
ALTER ROLE app_gerente       CONNECTION LIMIT 10;
ALTER ROLE app_admin         CONNECTION LIMIT 5;
ALTER ROLE auditor_readonly  CONNECTION LIMIT 5;


-- ============================================================================
-- 3. CREACIÓN DE ROLES DE PRIVILEGIOS (NOLOGIN) — LOS "GRUPOS" DE PERMISOS
-- ============================================================================
-- Nunca se otorgan privilegios directo al usuario LOGIN; siempre a un rol
-- NOLOGIN, que luego se asigna al usuario. Esto separa "quién se conecta" de
-- "qué puede hacer", y refleja el mismo espíritu del RBAC ya modelado en las
-- tablas roles/permisos/rol_permisos, pero a nivel de motor de base de datos.
-- ============================================================================
CREATE ROLE rol_dba           NOLOGIN;
CREATE ROLE rol_catalogo      NOLOGIN;  -- solo lectura del catálogo visible
CREATE ROLE rol_lector        NOLOGIN;  -- catálogo + su propia actividad
CREATE ROLE rol_bibliotecario NOLOGIN;  -- operación diaria de préstamos
CREATE ROLE rol_gerente       NOLOGIN;  -- cuentas + reportes + todo bibliotecario
CREATE ROLE rol_admin         NOLOGIN;  -- catálogos maestros y configuración técnica
CREATE ROLE rol_auditor       NOLOGIN;  -- solo lectura de bitácora


-- ============================================================================
-- 4. PRIVILEGIOS DE SISTEMA (A NIVEL DE SERVIDOR / BASE DE DATOS)
-- ============================================================================
-- Solo el DBA tiene privilegios de sistema. Si el backend fuera comprometido,
-- ningún rol de aplicación podría escalar privilegios a nivel de motor.
-- ============================================================================
ALTER ROLE admin_dba WITH CREATEDB CREATEROLE SUPERUSER;

ALTER ROLE app_catalogo      WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;
ALTER ROLE app_lector        WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;
ALTER ROLE app_bibliotecario WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;
ALTER ROLE app_gerente       WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;
ALTER ROLE app_admin         WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;
ALTER ROLE auditor_readonly  WITH NOCREATEDB NOCREATEROLE NOSUPERUSER;

-- Respaldos (BACKUP DATABASE): en PostgreSQL no es un privilegio GRANT-able
-- por sí solo; se habilita dando SELECT global al rol usado por pg_dump.
GRANT rol_dba TO admin_dba;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO rol_dba;

-- Solo el DBA puede crear objetos nuevos (futuras migraciones Flyway V2, V3…):
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CREATE ON SCHEMA public TO admin_dba;


-- ============================================================================
-- 5. PRIVILEGIOS SOBRE OBJETOS (TABLAS), POR ROL DE PRIVILEGIOS
-- ============================================================================

-- ---------- 5.1 rol_catalogo (visitante anónimo, landing + catálogo) ----------
GRANT SELECT (id, isbn, titulo, resumen, portada_url, anio_publicacion,
              editorial_id, idioma_id, estado_id, stock_disponible)
  ON libros TO rol_catalogo;
GRANT SELECT ON editoriales, idiomas, estados_libro, categorias, autores,
                libro_categorias, libro_autores
  TO rol_catalogo;
-- Sin acceso a usuarios, transacciones, seguridad ni auditoría.

-- ---------- 5.2 rol_lector (autenticado: favoritos, reservas, sugerencias) ----------
-- FIX: jerarquía de roles explícita en vez de tácita (ver detalle completo
-- justo antes de esta sección, en el bloque "3.1 Jerarquía de roles" — aquí
-- solo se deja constancia inline de que la sintaxis WITH INHERIT TRUE es la
-- misma cadena de heredado que antes producía el default implícito).
GRANT rol_catalogo TO rol_lector WITH INHERIT TRUE;

-- Su propia fila en usuarios (nunca password_hash):
GRANT SELECT (id, nombre, apellido, correo, correo_verificado, fecha_registro)
  ON usuarios TO rol_lector;
GRANT UPDATE (nombre, apellido) ON usuarios TO rol_lector;

-- Ver su propio rol asignado (no puede modificarlo, evita auto-escalamiento):
GRANT SELECT ON usuario_roles TO rol_lector;
GRANT SELECT ON roles TO rol_lector;

-- Favoritos: control total sobre sus propios registros (aislado con RLS, sec. 7)
GRANT SELECT, INSERT, DELETE ON favoritos TO rol_lector;

-- Reservaciones: puede crear y ver/cancelar las suyas (RLS)
GRANT SELECT, INSERT ON reservaciones TO rol_lector;
GRANT UPDATE (estado_reservacion_id) ON reservaciones TO rol_lector;

-- Préstamos y multas: solo lectura de su propio historial (RLS)
GRANT SELECT ON prestamos TO rol_lector;
GRANT SELECT ON multas TO rol_lector;

-- Sugerencias de adquisición: puede crear y ver las suyas (RLS)
GRANT SELECT, INSERT ON sugerencias_adquisicion TO rol_lector;

GRANT USAGE, SELECT ON SEQUENCE reservaciones_id_seq TO rol_lector;
GRANT USAGE, SELECT ON SEQUENCE sugerencias_adquisicion_id_seq TO rol_lector;

-- ---------- 5.3 rol_bibliotecario (operación diaria de mostrador) ----------
GRANT rol_catalogo TO rol_bibliotecario;

-- Catálogo: alta y edición completa (autocompletado ISBN vía backend)
GRANT SELECT, INSERT, UPDATE ON libros TO rol_bibliotecario;
GRANT SELECT, INSERT, UPDATE ON editoriales, idiomas, categorias, autores,
                                 libro_categorias, libro_autores
  TO rol_bibliotecario;
GRANT SELECT ON estados_libro TO rol_bibliotecario;  -- catálogo fijo, no editable

-- Operación de préstamos, devoluciones, multas y reservas de cualquier lector
GRANT SELECT, INSERT, UPDATE ON prestamos TO rol_bibliotecario;
GRANT SELECT, INSERT, UPDATE ON multas TO rol_bibliotecario;
GRANT SELECT, UPDATE ON reservaciones TO rol_bibliotecario;
GRANT SELECT ON estados_prestamo, estados_multa, estados_reservacion TO rol_bibliotecario;
GRANT SELECT ON configuracion_sistema TO rol_bibliotecario;  -- consulta tarifas/plazos

-- Puede ver datos básicos de cualquier lector para atenderlo, nunca su hash:
GRANT SELECT (id, nombre, apellido, correo, estado_id) ON usuarios TO rol_bibliotecario;

-- Revisa y responde sugerencias de adquisición de los lectores:
GRANT SELECT, UPDATE ON sugerencias_adquisicion TO rol_bibliotecario;

-- Sin acceso a favoritos (dato personal del lector, sin utilidad operativa)
-- ni a tablas de seguridad (roles, permisos, tokens_invalidos).

-- DELETE deliberadamente excluido en libros/préstamos/multas: se usa borrado
-- lógico (estado DADO_DE_BAJA / DEVUELTO / ANULADA) para preservar historial.

GRANT USAGE, SELECT ON SEQUENCE libros_id_seq, prestamos_id_seq, multas_id_seq TO rol_bibliotecario;
GRANT USAGE, SELECT ON SEQUENCE editoriales_id_seq, autores_id_seq TO rol_bibliotecario;

-- ---------- 5.4 rol_gerente (supervisión, cuentas de personal, reportes) ----------
-- FIX: WITH INHERIT TRUE explícito — ver nota de jerarquía de roles arriba.
GRANT rol_bibliotecario TO rol_gerente WITH INHERIT TRUE;

-- Gestión completa de cuentas (alta de personal, cambios de estado):
GRANT SELECT, INSERT, UPDATE ON usuarios TO rol_gerente;
REVOKE UPDATE (password_hash) ON usuarios FROM rol_gerente;  -- solo vía backend con hash

-- FIX: faltaba GRANT sobre la secuencia de usuarios. La sección anterior le
-- da INSERT en usuarios, pero usuarios.id es BIGSERIAL (usa
-- usuarios_id_seq.nextval() internamente); sin USAGE/SELECT sobre la
-- secuencia, el INSERT falla en la práctica con
-- "permission denied for sequence usuarios_id_seq" aunque el GRANT INSERT
-- sobre la tabla esté correcto.
GRANT USAGE, SELECT ON SEQUENCE usuarios_id_seq TO rol_gerente;

-- Gestión de asignación de roles de aplicación al personal:
GRANT SELECT, INSERT, DELETE ON usuario_roles TO rol_gerente;
GRANT SELECT ON roles, permisos, rol_permisos TO rol_gerente;

-- Catálogos maestros de estado (solo el gerente puede dar de baja definitiva):
GRANT SELECT, INSERT, UPDATE, DELETE ON estados_libro, estados_usuario TO rol_gerente;

-- Reportes: lectura total de auditoría (RLS no aplica; ve todo el negocio)
GRANT SELECT ON bitacora_auditoria TO rol_gerente;

-- ---------- 5.5 rol_admin (configuración técnica de la plataforma) ----------
-- Rol operativo, distinto de admin_dba: administra parámetros de negocio y
-- catálogos base de la plataforma, sin privilegios de sistema sobre PostgreSQL.
GRANT SELECT, INSERT, UPDATE ON configuracion_sistema TO rol_admin;
GRANT SELECT, INSERT, UPDATE, DELETE ON idiomas, categorias TO rol_admin;
GRANT SELECT ON roles, permisos, rol_permisos TO rol_admin;
GRANT SELECT ON bitacora_auditoria TO rol_admin;
GRANT SELECT ON tokens_invalidos, verificaciones_correo TO rol_admin;  -- soporte técnico

-- ---------- 5.6 rol_auditor (solo lectura, cumplimiento) ----------
GRANT SELECT ON bitacora_auditoria TO rol_auditor;
GRANT SELECT (id, nombre, apellido, correo, estado_id) ON usuarios TO rol_auditor;
GRANT SELECT ON usuario_roles, roles TO rol_auditor;
-- Sin acceso a libros/préstamos/multas: su función es auditar accesos y
-- cambios administrativos, no operar el negocio.


-- ============================================================================
-- 6. ASIGNACIÓN DE ROLES DE PRIVILEGIOS A LOS USUARIOS DE LOGIN
-- ============================================================================
GRANT rol_catalogo      TO app_catalogo;
GRANT rol_lector        TO app_lector;
GRANT rol_bibliotecario TO app_bibliotecario;
GRANT rol_gerente       TO app_gerente;
GRANT rol_admin         TO app_admin;
GRANT rol_auditor       TO auditor_readonly;
-- admin_dba ya recibió rol_dba en la sección 4.

-- FIX: Jerarquía de roles (INHERIT) — antes tácita, ahora documentada y
-- explícita. `GRANT rol_catalogo TO rol_lector` y `GRANT rol_bibliotecario
-- TO rol_gerente` (secciones 5.2 y 5.4) dependían del comportamiento
-- default de PostgreSQL: un rol NOLOGIN creado sin NOINHERIT hereda
-- automáticamente los privilegios de cualquier rol del que sea miembro, sin
-- necesitar SET ROLE. Ese default es exactamente lo que queremos aquí —
-- rol_lector debe poder usar los privilegios de rol_catalogo sin fricción,
-- y lo mismo rol_gerente respecto a rol_bibliotecario — así que NO se
-- cambia el comportamiento. Lo que sí se corrige es dejarlo tácito: desde
-- PostgreSQL 16, GRANT ... TO ... admite la cláusula WITH INHERIT
-- { TRUE | FALSE } para fijar explícitamente, por cada grant de membresía,
-- si esa herencia aplica — en vez de depender implícitamente del atributo
-- rolinherit del rol destino (que además podría cambiar más adelante con un
-- ALTER ROLE ... NOINHERIT y romper el supuesto silenciosamente). Los GRANT
-- de 5.2 y 5.4 ya se escribieron arriba como
-- `GRANT rol_catalogo TO rol_lector WITH INHERIT TRUE;` y
-- `GRANT rol_bibliotecario TO rol_gerente WITH INHERIT TRUE;` — mismo
-- efecto que antes, ahora imposible de confundir con un descuido.


-- ============================================================================
-- FIX: nota extensa sobre RLS + connection pooling, agregada antes de la
-- sección 7 (aplica a TODAS las políticas de esa sección).
-- ============================================================================
-- ============================================================================
-- ADVERTENCIA CRÍTICA: SET LOCAL, no SET, dentro de cada transacción
-- ============================================================================
-- Las políticas RLS de la sección 7 dependen de
-- current_setting('app.current_user_id', true). Ese valor lo debe fijar el
-- backend en CADA transacción, usando SIEMPRE:
--
--     SET LOCAL app.current_user_id = '42';
--
-- y NUNCA:
--
--     SET app.current_user_id = '42';        -- ¡PROHIBIDO EN ESTE PROYECTO!
--
-- Motivo (esto no es un matiz cosmético, es una fuga de datos entre
-- lectores si se hace mal): el backend Spring Boot usa un connection pool
-- (HikariCP). Las conexiones físicas a PostgreSQL se abren una sola vez y
-- se RECICLAN entre requests HTTP de usuarios distintos — el pool las
-- devuelve al pool al terminar la transacción, no las cierra. `SET`
-- (sin LOCAL) fija el parámetro a nivel de SESIÓN, es decir, a nivel de esa
-- conexión física completa: el valor persiste después del COMMIT/ROLLBACK y
-- sigue vigente cuando HikariCP le entrega esa misma conexión física a la
-- SIGUIENTE transacción — que bien puede pertenecer a OTRO usuario
-- autenticado. Resultado concreto: el lector B heredaría el
-- app.current_user_id del lector A y las políticas RLS de la sección 7
-- (favoritos_propios, reservaciones_propias, prestamos_propios,
-- multas_propias, sugerencias_propias) filtrarían — o peor, expondrían — los
-- datos del lector A al lector B, dependiendo de qué conexión reutilice el
-- pool. `SET LOCAL`, en cambio, fija el parámetro únicamente hasta el fin de
-- la transacción actual (COMMIT o ROLLBACK) y PostgreSQL lo revierte
-- automáticamente después, sin importar que la conexión física se reutilice
-- — que es exactamente el comportamiento que un pool de conexiones exige.
--
-- Implicación práctica adicional: `SET LOCAL` solo tiene efecto dentro de un
-- bloque transaccional explícito. Si el backend ejecuta la query en modo
-- autocommit (sin una transacción abierta), `SET LOCAL` no falla, pero
-- tampoco persiste ni un instante — quedaría sin efecto y la política RLS
-- evaluaría current_setting(...) como NULL (de ahí el segundo argumento
-- `true` en current_setting, que evita un error y en su lugar devuelve NULL,
-- lo que a su vez hace que la política no matchee ninguna fila — falla
-- cerrado, no abierto). Por eso el método de servicio que fija esta variable
-- DEBE estar dentro de un @Transactional real.
--
-- Pseudo-código ilustrativo (SOLO para documentar la intención en este
-- archivo SQL; la implementación Java real queda para cuando se decida
-- migrar a la arquitectura multi-rol/multi-datasource — no se implementa
-- todavía):
--
--     @Service
--     public class ReservacionService {
--
--         @Transactional
--         public List<ReservacionDTO> listarMisReservaciones(Long usuarioId) {
--             // 1. Dentro de la MISMA transacción/conexión que las queries
--             //    de negocio de abajo — nunca en un método/conexión aparte.
--             jdbcTemplate.update("SET LOCAL app.current_user_id = ?", usuarioId);
--
--             // 2. La conexión física ya trae el rol_lector de PostgreSQL
--             //    asignado (ver sección 6); RLS aplica automáticamente,
--             //    sin necesidad de un WHERE usuario_id = :usuarioId manual.
--             return reservacionRepository.findAll();
--
--             // 3. Al terminar la transacción (COMMIT normal de Spring),
--             //    PostgreSQL descarta app.current_user_id de esta conexión
--             //    física antes de que HikariCP la devuelva al pool.
--         }
--     }
--
-- ============================================================================


-- ============================================================================
-- 7. SEGURIDAD A NIVEL DE FILA (ROW LEVEL SECURITY) — AISLAMIENTO DE LECTORES
-- ============================================================================
-- Un lector jamás debe poder leer o modificar los favoritos, reservas,
-- préstamos, multas o sugerencias de OTRO lector, aunque ambos compartan el
-- mismo rol de privilegios (rol_lector).
-- ============================================================================

ALTER TABLE favoritos ENABLE ROW LEVEL SECURITY;
ALTER TABLE favoritos FORCE ROW LEVEL SECURITY;

ALTER TABLE reservaciones ENABLE ROW LEVEL SECURITY;
ALTER TABLE reservaciones FORCE ROW LEVEL SECURITY;

ALTER TABLE prestamos ENABLE ROW LEVEL SECURITY;
ALTER TABLE prestamos FORCE ROW LEVEL SECURITY;

ALTER TABLE multas ENABLE ROW LEVEL SECURITY;
ALTER TABLE multas FORCE ROW LEVEL SECURITY;

ALTER TABLE sugerencias_adquisicion ENABLE ROW LEVEL SECURITY;
ALTER TABLE sugerencias_adquisicion FORCE ROW LEVEL SECURITY;

-- El backend, tras validar el JWT, setea el id del lector autenticado
-- (ver bloque de advertencia arriba: SIEMPRE SET LOCAL, nunca SET):
--   SET LOCAL app.current_user_id = '42';
-- Las políticas comparan la columna usuario_id de cada tabla contra ese valor.

CREATE POLICY favoritos_propios ON favoritos
    FOR ALL TO rol_lector
    USING (usuario_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY reservaciones_propias ON reservaciones
    FOR ALL TO rol_lector
    USING (usuario_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY prestamos_propios ON prestamos
    FOR SELECT TO rol_lector
    USING (usuario_id = current_setting('app.current_user_id', true)::BIGINT);

CREATE POLICY multas_propias ON multas
    FOR SELECT TO rol_lector
    USING (
        prestamo_id IN (
            SELECT id FROM prestamos
            WHERE usuario_id = current_setting('app.current_user_id', true)::BIGINT
        )
    );

CREATE POLICY sugerencias_propias ON sugerencias_adquisicion
    FOR ALL TO rol_lector
    USING (usuario_id = current_setting('app.current_user_id', true)::BIGINT);

-- El bibliotecario y el gerente ven TODAS las filas necesarias para operar
-- (sin RLS sobre favoritos: no forman parte de su función operativa):
CREATE POLICY reservaciones_staff ON reservaciones
    FOR ALL TO rol_bibliotecario, rol_gerente
    USING (true);

CREATE POLICY prestamos_staff ON prestamos
    FOR ALL TO rol_bibliotecario, rol_gerente
    USING (true);

CREATE POLICY multas_staff ON multas
    FOR ALL TO rol_bibliotecario, rol_gerente
    USING (true);

CREATE POLICY sugerencias_staff ON sugerencias_adquisicion
    FOR ALL TO rol_bibliotecario, rol_gerente
    USING (true);


-- ============================================================================
-- 8. PRIVILEGIOS SOBRE SECUENCIAS (SERIAL/BIGSERIAL)
-- ============================================================================
-- Sin este GRANT, un INSERT falla aunque exista GRANT INSERT en la tabla,
-- porque SERIAL/BIGSERIAL dependen internamente de nextval().
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rol_bibliotecario;
-- Nota: favoritos usa PK compuesta (usuario_id, libro_id) sin columna SERIAL,
-- por lo que no requiere GRANT sobre secuencia alguna.


-- ============================================================================
-- 9. VERIFICACIÓN RÁPIDA (para la sustentación / demo en clase)
-- ============================================================================
-- \du                    -- lista todos los roles y sus atributos
-- \dp libros             -- privilegios otorgados sobre la tabla libros
--
-- SET ROLE rol_lector;
-- SELECT * FROM usuarios;       -- solo columnas permitidas
-- SELECT * FROM reservaciones;  -- solo filas propias (RLS)
-- RESET ROLE;
-- ============================================================================
