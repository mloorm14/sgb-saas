-- V49: triggers de auditoria de las 12 tablas de negocio (Fase 2)
-- db/auditoria-triggers.sql documenta fn_auditoria_generica() atada a 21
-- tablas desde su creacion, pero solo 9 de esas 21 (las administrativas/de
-- respaldo) llegaron a Flyway en V40__auditoria_triggers_faltantes ("Fase 1").
-- Las 12 restantes -- las tablas de negocio mas importantes del sistema --
-- nunca se migraron (ver docs/observaciones/OBSERVACIONES.md, OBS-28).
-- Hasta esta migracion, esas 12 tablas se auditaban solo via INSERT manual
-- en los Services de Java; esa via manual se retira en el mismo cambio que
-- introduce esta migracion (ver los Services tocados) porque, con el
-- trigger presente, el INSERT manual pasaria a duplicar cada evento.
--
-- Idempotente: DROP IF EXISTS antes de CREATE, mismo patron que V40.
-- Reutiliza fn_auditoria_generica() existente desde V39_2 (SECURITY
-- DEFINER, search_path=public) -- no se toca su definicion.
-- Definiciones copiadas tal cual de db/auditoria-triggers.sql seccion 2
-- (las primeras 12, antes del comentario "V40 agrega...").

DROP TRIGGER IF EXISTS trg_auditoria_usuarios ON usuarios;
CREATE TRIGGER trg_auditoria_usuarios
    AFTER INSERT OR UPDATE OR DELETE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_libros ON libros;
CREATE TRIGGER trg_auditoria_libros
    AFTER INSERT OR UPDATE OR DELETE ON libros
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_prestamos ON prestamos;
CREATE TRIGGER trg_auditoria_prestamos
    AFTER INSERT OR UPDATE OR DELETE ON prestamos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_multas ON multas;
CREATE TRIGGER trg_auditoria_multas
    AFTER INSERT OR UPDATE OR DELETE ON multas
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_reservaciones ON reservaciones;
CREATE TRIGGER trg_auditoria_reservaciones
    AFTER INSERT OR UPDATE OR DELETE ON reservaciones
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_roles ON roles;
CREATE TRIGGER trg_auditoria_roles
    AFTER INSERT OR UPDATE OR DELETE ON roles
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_usuario_roles ON usuario_roles;
CREATE TRIGGER trg_auditoria_usuario_roles
    AFTER INSERT OR UPDATE OR DELETE ON usuario_roles
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_permisos ON permisos;
CREATE TRIGGER trg_auditoria_permisos
    AFTER INSERT OR UPDATE OR DELETE ON permisos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_rol_permisos ON rol_permisos;
CREATE TRIGGER trg_auditoria_rol_permisos
    AFTER INSERT OR UPDATE OR DELETE ON rol_permisos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_notificaciones ON notificaciones;
CREATE TRIGGER trg_auditoria_notificaciones
    AFTER INSERT OR UPDATE OR DELETE ON notificaciones
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_sugerencias_adquisicion ON sugerencias_adquisicion;
CREATE TRIGGER trg_auditoria_sugerencias_adquisicion
    AFTER INSERT OR UPDATE OR DELETE ON sugerencias_adquisicion
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_registro_danos ON registro_danos;
CREATE TRIGGER trg_auditoria_registro_danos
    AFTER INSERT OR UPDATE OR DELETE ON registro_danos
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();
