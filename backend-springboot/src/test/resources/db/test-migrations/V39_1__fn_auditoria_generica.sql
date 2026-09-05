-- V39_1: fn_auditoria_generica() disponible antes de V40 (fresh-DB fix).
-- V40__auditoria_triggers_faltantes crea triggers que referencian esta funcion.
-- En BDs frescas (Testcontainers, nuevos deploys) Flyway corre V40 sin que la
-- funcion exista (solo vivia en db/auditoria-triggers.sql, fuera de Flyway) y
-- la migracion falla. 39.1 < 40: corre antes de V40 en BDs nuevas; en BDs ya
-- migradas Flyway la ignora (outOfOrder=false) y el OR REPLACE la hace segura
-- si algun dia corre. Definicion copiada integra de db/auditoria-triggers.sql.

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
