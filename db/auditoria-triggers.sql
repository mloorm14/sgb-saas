-- ============================================================================
-- TRIGGERS DE AUDITORÍA A NIVEL DE BASE DE DATOS
-- Proyecto: SGB-SaaS (Sistema de Gestión Bibliotecaria Web)
-- Materia: Administración de Bases de Datos (Requisito 5: bitácora de
--          auditoría)
-- Motor: PostgreSQL 16
-- Base: db/schema.sql (bitacora_auditoria definida en
--       database/migrations/V2__rbac_normalizado.sql, tipos ampliados en
--       database/migrations/V7__ampliar_tipos_bitacora_auditoria.sql)
-- ============================================================================
-- BRECHA QUE CIERRA ESTE ARCHIVO: hasta ahora bitacora_auditoria solo se
-- llenaba desde (a) código Java explícito (AuthService en login/logout,
-- UsuarioAdminService, etc.) y (b) un puñado de INSERT manuales dentro de
-- procedimientos almacenados puntuales (ver database/migrations/R__stored_
-- procedures.sql, función sp_anular_multa). No existía NINGÚN trigger de
-- base de datos: un UPDATE/DELETE/INSERT ejecutado directamente por SQL
-- (psql, un cliente de BD, una migración futura mal escrita, o cualquier
-- credencial con privilegio de escritura que no pase por la capa Java)
-- no dejaba NINGÚN rastro. Esto es una brecha real de defensa en
-- profundidad: la auditoría a nivel de BD no debe depender de que la capa
-- de aplicación sea siempre quien hace el cambio (mismo principio que
-- db/roles-privilegios.sql aplica con RBAC + RLS a nivel de motor,
-- independiente del RBAC de aplicación en las tablas roles/permisos).
--
-- Este archivo agrega UNA función de trigger genérica y la ata a las 12
-- tablas de negocio más relevantes para auditoría (ver sección 2). No
-- reemplaza la auditoría de aplicación (LOGIN_OK/LOGIN_FAIL/LOGOUT y el
-- registro de IP siguen siendo responsabilidad exclusiva del backend, que
-- sí tiene acceso a la petición HTTP) — es su complemento a nivel de BD
-- para operaciones DML directas sobre las tablas.
-- ============================================================================


-- ============================================================================
-- 1. FUNCIÓN GENÉRICA DE TRIGGER
-- ============================================================================
-- Una sola función, reutilizada por los 12 triggers de la sección 2, en vez
-- de una función por tabla: la lógica (determinar tipo de operación, capturar
-- el usuario de sesión, serializar la fila) es idéntica en las 12 tablas;
-- lo único que cambia es TG_TABLE_NAME/TG_OP/NEW/OLD, que PostgreSQL ya
-- inyecta automáticamente en cada invocación.
--
-- SECURITY DEFINER (obligatorio, no opcional): db/roles-privilegios.sql
-- solo otorga GRANT SELECT sobre bitacora_auditoria a rol_gerente,
-- rol_admin y rol_auditor — NINGÚN rol de aplicación (rol_lector,
-- rol_bibliotecario incluido) tiene GRANT INSERT sobre esa tabla. Sin
-- SECURITY DEFINER, esta función corre por defecto como SECURITY INVOKER,
-- es decir con los privilegios del rol que disparó el UPDATE/INSERT/DELETE
-- original (p.ej. app_lector). El INSERT INTO bitacora_auditoria de más
-- abajo fallaría con "permission denied for table bitacora_auditoria" en
-- CUALQUIER escritura legítima hecha por un rol de aplicación normal — y
-- como el trigger corre en la misma transacción que el UPDATE/INSERT/
-- DELETE original, ese error no solo rompe la auditoría: aborta también
-- la operación de negocio que la disparó (ver Caso 3 de la sección 4 de
-- verificación, que reproduce este fallo real antes de aplicar el fix).
-- SECURITY DEFINER hace que la función corra con los privilegios de su dueño (quien la
-- CREATE, típicamente admin_dba/la migración), que sí tiene acceso de
-- escritura a bitacora_auditoria — el mismo patrón estándar de Postgres
-- para funciones/triggers de auditoría que escriben en una tabla de log a
-- la que los llamantes ordinarios no tienen acceso directo.
-- ============================================================================
-- search_path fijo como ATRIBUTO de la función (SET search_path = public
-- aquí, no como sentencia dentro del BEGIN...END): best practice estándar
-- de Postgres para toda función SECURITY DEFINER (ver documentación
-- oficial de CREATE FUNCTION, sección "Writing SECURITY DEFINER Functions
-- Safely"). Sin esto, la función heredaría el search_path de sesión de
-- quien la invoca; un rol de aplicación con privilegio CREATE en algún
-- esquema anterior en ese search_path podría crear un objeto (p.ej. una
-- función to_jsonb() o un operador -) que esta función terminaría
-- invocando sin saberlo, pero con los privilegios elevados del dueño
-- (admin_dba) — un ataque clásico de "search_path hijacking" contra
-- funciones SECURITY DEFINER. Se fija como atributo (no como `SET
-- search_path = public;` dentro del cuerpo) a propósito: como atributo,
-- Postgres lo aplica y lo revierte automáticamente solo durante esta
-- invocación (equivalente a un SET LOCAL con alcance a la función); un
-- `SET` normal ejecutado dentro del cuerpo persistiría en la sesión/
-- conexión física para el resto de sentencias del pool — exactamente el
-- mismo riesgo de fuga entre requests por conexión reciclada que
-- db/roles-privilegios.sql ya documenta en detalle para app.current_user_id
-- (sección "ADVERTENCIA CRÍTICA: SET LOCAL, no SET").
CREATE OR REPLACE FUNCTION fn_auditoria_generica()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_usuario_id  BIGINT;
    v_registro_id BIGINT;
    v_old_data    JSONB;
    v_new_data    JSONB;
    v_detalles    JSONB;
BEGIN
    -- Usuario de sesión: mismo patrón defensivo que las políticas RLS de
    -- db/roles-privilegios.sql (sección 7) — current_setting(..., true)
    -- devuelve NULL en vez de lanzar error cuando la variable no fue
    -- fijada con SET LOCAL, en lugar de reinventar el manejo de NULL aquí.
    -- Con una conexión directa del DBA (sin paso por el backend) esta
    -- variable nunca existe, así que usuario_id queda NULL a propósito
    -- (ver verificación al final de este archivo) en vez de fallar el
    -- INSERT/UPDATE/DELETE original.
    v_usuario_id := current_setting('app.current_user_id', true)::BIGINT;

    -- registro_id: la inmensa mayoría de las tablas tiene una PK simple
    -- `id`. usuario_roles y rol_permisos son la excepción (PK compuesta,
    -- ver V2__rbac_normalizado.sql y db/schema.sql — no existe columna
    -- `id`). En vez de listar por nombre qué tablas tienen `id` y cuáles
    -- no (frágil ante tablas nuevas), se extrae el campo desde la
    -- representación JSON de la fila: to_jsonb(...)->>'id' devuelve NULL
    -- sin error cuando la clave no existe, que es exactamente lo que
    -- queremos para esas dos tablas de PK compuesta (el detalle completo
    -- de la fila, incluida la clave compuesta, igual queda en `detalles`).
    IF TG_OP = 'DELETE' THEN
        v_registro_id := (to_jsonb(OLD) ->> 'id')::BIGINT;
    ELSE
        v_registro_id := (to_jsonb(NEW) ->> 'id')::BIGINT;
    END IF;

    -- Serialización de la fila. Para UPDATE se guardan AMBAS versiones
    -- (antes/después) para que el diff sea reconstruible; guardar solo la
    -- fila nueva perdería qué cambió exactamente, que es el punto central
    -- de una bitácora de auditoría.
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        v_new_data := to_jsonb(NEW);
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        v_old_data := to_jsonb(OLD);
    END IF;

    -- REGLA DURA DE ESTE PROYECTO (ver db/roles-privilegios.sql, GRANT/
    -- REVOKE de password_hash en toda la sección 5): password_hash nunca
    -- se expone a ningún rol ni salida, ni siquiera de lectura interna.
    -- Este trigger corre con los privilegios del dueño de la función
    -- (normalmente el owner de la tabla / el DBA), por lo que SÍ tiene
    -- acceso crudo a password_hash vía NEW/OLD — se elimina explícitamente
    -- del JSON antes de guardarlo, para que la bitácora tampoco se
    -- convierta en una fuga lateral del hash.
    IF TG_TABLE_NAME = 'usuarios' THEN
        v_old_data := v_old_data - 'password_hash';
        v_new_data := v_new_data - 'password_hash';
    END IF;

    IF TG_OP = 'INSERT' THEN
        v_detalles := v_new_data;
    ELSIF TG_OP = 'UPDATE' THEN
        v_detalles := jsonb_build_object('antes', v_old_data, 'despues', v_new_data);
    ELSE -- DELETE
        v_detalles := v_old_data;
    END IF;

    -- TG_OP ya viene como 'INSERT'/'UPDATE'/'DELETE', los mismos literales
    -- que acepta el CHECK de tipo_operacion (V2__rbac_normalizado.sql /
    -- V7__ampliar_tipos_bitacora_auditoria.sql) — no hace falta traducirlo.
    -- ip_origen queda NULL: un trigger de BD no tiene acceso a la petición
    -- HTTP que originó el cambio. Ese dato solo lo captura el backend
    -- (AuthService, en los eventos LOGIN_OK/LOGIN_FAIL/LOGOUT que inserta
    -- manualmente) — este trigger es el complemento a nivel de BD para el
    -- resto de operaciones DML, no un reemplazo de esa captura.
    INSERT INTO bitacora_auditoria
        (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles, ip_origen)
    VALUES
        (v_usuario_id, TG_OP, TG_TABLE_NAME, v_registro_id, v_detalles::TEXT, NULL);

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;


-- ============================================================================
-- 2. TRIGGERS: se atan a las 12 tablas de negocio auditables
-- ============================================================================
-- AFTER (no BEFORE): la auditoría deja constancia de lo que efectivamente
-- se escribió, no de una intención que podría todavía fallar por otro
-- trigger/constraint posterior. FOR EACH ROW: bitacora_auditoria necesita
-- una fila por registro afectado, no una sola fila por sentencia.
-- ============================================================================

CREATE TRIGGER trg_auditoria_usuarios
    AFTER INSERT OR UPDATE OR DELETE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_libros
    AFTER INSERT OR UPDATE OR DELETE ON libros
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_prestamos
    AFTER INSERT OR UPDATE OR DELETE ON prestamos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_multas
    AFTER INSERT OR UPDATE OR DELETE ON multas
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_reservaciones
    AFTER INSERT OR UPDATE OR DELETE ON reservaciones
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_roles
    AFTER INSERT OR UPDATE OR DELETE ON roles
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_usuario_roles
    AFTER INSERT OR UPDATE OR DELETE ON usuario_roles
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_permisos
    AFTER INSERT OR UPDATE OR DELETE ON permisos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_rol_permisos
    AFTER INSERT OR UPDATE OR DELETE ON rol_permisos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_notificaciones
    AFTER INSERT OR UPDATE OR DELETE ON notificaciones
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_sugerencias_adquisicion
    AFTER INSERT OR UPDATE OR DELETE ON sugerencias_adquisicion
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

CREATE TRIGGER trg_auditoria_registro_danos
    AFTER INSERT OR UPDATE OR DELETE ON registro_danos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();


-- ============================================================================
-- 3. CONVIVENCIA CON LOS INSERT MANUALES YA EXISTENTES (DUPLICACIÓN CONOCIDA)
-- ============================================================================
-- database/migrations/R__stored_procedures.sql ya inserta manualmente en
-- bitacora_auditoria dentro de sp_anular_multa (UPDATE multas ... seguido
-- de un INSERT INTO bitacora_auditoria explícito documentando el motivo de
-- la anulación). Ese mismo UPDATE ahora TAMBIÉN dispara trg_auditoria_multas
-- de este archivo, así que una sola anulación de multa queda registrada
-- DOS VECES en bitacora_auditoria: una fila del INSERT manual del
-- procedimiento (con el motivo en texto libre) y otra fila del trigger
-- genérico (con el JSON antes/después de la fila completa).
--
-- Esto es una duplicación conocida y ACEPTADA para el alcance de este
-- proyecto universitario, no un bug a corregir aquí. Evitarla requeriría
-- alguna forma de que el procedimiento le "avise" al trigger que no vuelva
-- a auditar (típicamente una variable de sesión tipo
-- `SET LOCAL app.skip_auditoria_trigger = 'true'` chequeada al inicio de
-- fn_auditoria_generica(), o deshabilitar/rehabilitar el trigger dentro de
-- la transacción del procedimiento) — complejidad adicional
-- desproporcionada frente al problema real: dos filas de auditoría para el
-- mismo evento no oculta información ni la corrompe, solo la duplica. Un
-- lector de la bitácora (GERENTE/ADMIN vía AuditoriaService, o el
-- auditor_readonly de db/roles-privilegios.sql) ve dos eventos consecutivos
-- con el mismo tabla_afectada/registro_id y timestamps casi idénticos; es
-- ruido, no un error de auditoría.
-- ============================================================================


-- ============================================================================
-- 4. VERIFICACIÓN RÁPIDA (para la sustentación / demo en clase)
-- ============================================================================
-- Caso 1: cambio hecho "como si fuera" el backend, con app.current_user_id
-- fijado (igual que el patrón SET LOCAL de db/roles-privilegios.sql sección
-- 7). El trigger debe capturar usuario_id = 1 y el JSON antes/después.
--
--   BEGIN;
--   SET LOCAL app.current_user_id = '1';
--   UPDATE usuarios SET nombre = 'Admin Actualizado' WHERE id = 1;
--   SELECT id, usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles
--     FROM bitacora_auditoria
--    ORDER BY id DESC LIMIT 1;
--   COMMIT;
--
-- Caso 2: cambio hecho SIN app.current_user_id (simula una conexión directa
-- de admin_dba, fuera del backend). El trigger no debe fallar: usuario_id
-- debe quedar NULL, no lanzar excepción.
--
--   UPDATE usuarios SET nombre = 'Admin Sin Sesion' WHERE id = 1;
--   SELECT id, usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles
--     FROM bitacora_auditoria
--    ORDER BY id DESC LIMIT 1;
--
-- \d usuarios                         -- confirma que el trigger quedó adjunto
-- SELECT tgname, tgrelid::regclass FROM pg_trigger
--  WHERE tgname LIKE 'trg_auditoria_%' ORDER BY tgname;
--
-- Caso 3 (el que de verdad importa para producción): conectado como un rol
-- de APLICACIÓN real, no como admin_dba/dueño de los objetos. Requiere
-- haber aplicado antes db/roles-privilegios.sql (crea app_lector/rol_lector
-- y sus GRANT reales) y tener una reservación cuyo usuario_id coincida con
-- el usuario de sesión (política reservaciones_propias, sección 7 de ese
-- archivo). Sin SECURITY DEFINER en fn_auditoria_generica(), este UPDATE
-- falla con "permission denied for table bitacora_auditoria" -- porque
-- rol_lector nunca recibió GRANT INSERT sobre bitacora_auditoria (solo
-- rol_gerente/rol_admin/rol_auditor tienen SELECT) -- y esa falla aborta
-- también el UPDATE legítimo, no solo la auditoría. Con SECURITY DEFINER
-- (este archivo), el mismo UPDATE debe tener éxito Y dejar la fila en
-- bitacora_auditoria:
--
--   SET ROLE app_lector;
--   BEGIN;
--   SET LOCAL app.current_user_id = '<id del usuario dueño de la reserva>';
--   UPDATE reservaciones SET estado_reservacion_id = 2 WHERE id = <id>;
--   COMMIT;
--   RESET ROLE;
--   SELECT id, usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles
--     FROM bitacora_auditoria
--    WHERE tabla_afectada = 'reservaciones'
--    ORDER BY id DESC LIMIT 1;
-- ============================================================================
