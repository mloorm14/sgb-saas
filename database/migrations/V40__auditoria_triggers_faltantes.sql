-- V40: triggers de auditoria faltantes (Fase 1)
-- Cubre tablas creadas en V30-V35 y catalogos de dano V14/V25 que no tenian
-- trg_auditoria_* en db/auditoria-triggers.sql (que solo cubria 12 tablas).
-- Idempotente: DROP IF EXISTS antes de CREATE (Flyway baseline + docker-entrypoint).
-- Reutiliza fn_auditoria_generica() existente (SECURITY DEFINER, search_path=public).

-- ── Proveedores (V35) ──────────────────────────────
DROP TRIGGER IF EXISTS trg_auditoria_proveedores ON proveedores;
CREATE TRIGGER trg_auditoria_proveedores
    AFTER INSERT OR UPDATE OR DELETE ON proveedores
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

-- ── Configuracion del sistema (V2/V4) ──────────────
DROP TRIGGER IF EXISTS trg_auditoria_configuracion_sistema ON configuracion_sistema;
CREATE TRIGGER trg_auditoria_configuracion_sistema
    AFTER INSERT OR UPDATE OR DELETE ON configuracion_sistema
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

-- ── Tipos / categorias de dano (V14/V25) ───────────
DROP TRIGGER IF EXISTS trg_auditoria_tipos_dano ON tipos_dano;
CREATE TRIGGER trg_auditoria_tipos_dano
    AFTER INSERT OR UPDATE OR DELETE ON tipos_dano
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_categorias_dano ON categorias_dano;
CREATE TRIGGER trg_auditoria_categorias_dano
    AFTER INSERT OR UPDATE OR DELETE ON categorias_dano
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

-- ── Respaldos (V30-V34) ────────────────────────────
DROP TRIGGER IF EXISTS trg_auditoria_backups ON backups;
CREATE TRIGGER trg_auditoria_backups
    AFTER INSERT OR UPDATE OR DELETE ON backups
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_backups_tablas ON backups_tablas;
CREATE TRIGGER trg_auditoria_backups_tablas
    AFTER INSERT OR UPDATE OR DELETE ON backups_tablas
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_backup_programacion ON backup_programacion;
CREATE TRIGGER trg_auditoria_backup_programacion
    AFTER INSERT OR UPDATE OR DELETE ON backup_programacion
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_configuracion_respaldo ON configuracion_respaldo;
CREATE TRIGGER trg_auditoria_configuracion_respaldo
    AFTER INSERT OR UPDATE OR DELETE ON configuracion_respaldo
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();

DROP TRIGGER IF EXISTS trg_auditoria_registros_respaldo ON registros_respaldo;
CREATE TRIGGER trg_auditoria_registros_respaldo
    AFTER INSERT OR UPDATE OR DELETE ON registros_respaldo
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_generica();
