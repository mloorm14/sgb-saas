-- ============================================================================
-- sp_anular_multa
-- Tipo: función (LANGUAGE plpgsql), múltiples parámetros de salida (OUT).
--
-- Propósito: anular una multa pendiente (p.ej. por decisión administrativa),
-- dejando constancia en la bitácora de auditoría. Requiere rol GERENTE o
-- ADMIN — verificado aquí como segunda barrera además del @PreAuthorize
-- que aplicará el backend.
--
-- Parámetros (nombrados):
--   p_multa_id     BIGINT       — multa a anular
--   p_motivo       VARCHAR(255) — justificación, se guarda en multas.observaciones
--                                 y en bitacora_auditoria.detalles. Se usa
--                                 siempre como VALOR insertado (bind), nunca
--                                 concatenado dentro de una cláusula WHERE u
--                                 otra sentencia SQL — no constituye SQL
--                                 dinámico ni concatenación de entrada de
--                                 usuario en una query ejecutable.
--   p_rol_ejecutor VARCHAR(30)  — rol del usuario que ejecuta la operación
--
-- Retorno (OUT):
--   o_multa_id             BIGINT  — mismo id recibido, para confirmación
--   o_usuario_desbloqueado BOOLEAN — true si el usuario volvió a ACTIVO
--
-- Errores:
--   LB422 — p_rol_ejecutor no es GERENTE ni ADMIN
--   LB404 — la multa no existe
--   LB409 — la multa no está en estado PENDIENTE
--
-- Tablas afectadas: multas (lectura+UPDATE), prestamos (lectura),
-- estados_multa (lectura), usuarios (UPDATE estado condicional),
-- estados_usuario (lectura), bitacora_auditoria (INSERT).
--
-- Nota: bitacora_auditoria.usuario_id se deja en NULL porque esta función
-- no recibe el id del usuario ejecutor (solo su rol, p_rol_ejecutor) —
-- si se requiere trazar exactamente QUIÉN anuló la multa, agregar un
-- parámetro p_ejecutor_id BIGINT en una futura revisión de este archivo.
-- ============================================================================
CREATE OR REPLACE FUNCTION sp_anular_multa(
    p_multa_id BIGINT,
    p_motivo VARCHAR(255),
    p_rol_ejecutor VARCHAR(30),
    OUT o_multa_id BIGINT,
    OUT o_usuario_desbloqueado BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_anulada_id         INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
BEGIN
    IF p_rol_ejecutor <> 'GERENTE' AND p_rol_ejecutor <> 'ADMIN' THEN
        RAISE EXCEPTION 'Solo GERENTE o ADMIN puede anular multas' USING ERRCODE = 'LB422';
    END IF;

    SELECT m.estado_multa_id, p.usuario_id
      INTO v_estado_multa_id, v_usuario_id
      FROM multas m
      JOIN prestamos p ON p.id = m.prestamo_id
     WHERE m.id = p_multa_id
     FOR UPDATE OF m;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La multa % no existe', p_multa_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_pendiente_id FROM estados_multa WHERE nombre = 'PENDIENTE';

    IF v_estado_multa_id <> v_estado_pendiente_id THEN
        RAISE EXCEPTION 'La multa % no esta pendiente, no se puede anular', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    SELECT id INTO v_estado_anulada_id FROM estados_multa WHERE nombre = 'ANULADA';

    UPDATE multas
       SET estado_multa_id = v_estado_anulada_id,
           observaciones = p_motivo
     WHERE id = p_multa_id;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;

    INSERT INTO bitacora_auditoria (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles)
    VALUES (NULL, 'UPDATE', 'multas', p_multa_id, 'Multa anulada: ' || p_motivo);
END;
$$;
