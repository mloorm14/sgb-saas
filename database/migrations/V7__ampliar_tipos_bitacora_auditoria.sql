-- Modulo 9.5: AuthService.verificarCorreo() inserta un evento
-- CORREO_VERIFICADO en bitacora_auditoria, pero el CHECK original
-- (db/schema.sql) solo permite INSERT/UPDATE/DELETE/LOGIN_OK/LOGIN_FAIL/
-- LOGOUT. Se reemplaza la restriccion por una que incluye el valor nuevo.
--
-- IMPORTANTE: el nombre "bitacora_auditoria_tipo_operacion_check" es el
-- que Postgres asigna automaticamente a un CHECK inline sin nombre
-- explicito (convencion <tabla>_<columna>_check). Si al aplicar esta
-- migracion en un ambiente existente el DROP falla por nombre distinto,
-- verificar el nombre real con:
--   SELECT conname FROM pg_constraint WHERE conrelid = 'bitacora_auditoria'::regclass AND contype = 'c';
ALTER TABLE bitacora_auditoria
    DROP CONSTRAINT IF EXISTS bitacora_auditoria_tipo_operacion_check;

ALTER TABLE bitacora_auditoria
    ADD CONSTRAINT bitacora_auditoria_tipo_operacion_check
    CHECK (tipo_operacion IN
           ('INSERT', 'UPDATE', 'DELETE', 'LOGIN_OK', 'LOGIN_FAIL', 'LOGOUT',
            'CORREO_VERIFICADO'));
