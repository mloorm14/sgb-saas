-- V50: historial dedicado de motivos de cambio de estado/eliminación de usuario.
-- Contexto (ver docs/observaciones/OBSERVACIONES.md, OBS-28): al retirar en
-- V49 el INSERT manual a bitacora_auditoria de UsuarioAdminService, el
-- parámetro "motivo" de cambiarEstado()/eliminarUsuario() dejó de
-- persistirse en cualquier lado -- no es columna de usuarios, y
-- trg_auditoria_usuarios (trigger genérico) solo ve columnas de la fila
-- (antes/después), no puede reconstruir un parámetro suelto que el llamante
-- pasó por fuera de la fila.
--
-- Se resuelve con una tabla de historial dedicada (no una columna en
-- usuarios: una columna se sobreescribiría en cada cambio y perdería el
-- motivo de cambios anteriores).
--
-- Esta tabla es COMPLEMENTARIA a bitacora_auditoria/trg_auditoria_usuarios,
-- no un reemplazo: trg_auditoria_usuarios sigue auditando el UPDATE de la
-- fila de usuarios como hasta ahora; esta tabla solo guarda el motivo que
-- el trigger no puede ver.

CREATE TABLE IF NOT EXISTS usuario_motivos_cambio (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    tipo_cambio     VARCHAR(20) NOT NULL CHECK (tipo_cambio IN ('CAMBIO_ESTADO', 'ELIMINACION')),
    estado_anterior INTEGER REFERENCES estados_usuario(id),
    estado_nuevo    INTEGER REFERENCES estados_usuario(id),
    motivo          TEXT,
    ejecutado_por   BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ON DELETE RESTRICT en usuario_id/ejecutado_por (mismo criterio que
-- backups.creado_por en V30__backup_tablas.sql): usuarios nunca se borra
-- físicamente -- eliminarUsuario() hace soft-delete (pasa el estado a
-- INACTIVO), así que RESTRICT nunca debería dispararse en operación normal;
-- solo evita que un DELETE físico accidental (fuera de la app) borre en
-- silencio el historial de motivos.

-- Se va a consultar "dame el historial de motivos de este usuario".
CREATE INDEX IF NOT EXISTS idx_usuario_motivos_cambio_usuario_id ON usuario_motivos_cambio (usuario_id);
