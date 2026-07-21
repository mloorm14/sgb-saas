-- ============================================================================
-- sp_pagar_multa
-- Tipo: función (LANGUAGE plpgsql), múltiples parámetros de salida (OUT).
--
-- Propósito: registrar el pago de una multa pendiente y, si el usuario no
-- tiene otras multas pendientes, reactivar su cuenta (estado ACTIVO).
--
-- Parámetros (nombrados):
--   p_multa_id BIGINT — multa a pagar
--
-- Retorno (OUT):
--   o_multa_id             BIGINT  — mismo id recibido, para confirmación
--   o_usuario_desbloqueado BOOLEAN — true si el usuario volvió a ACTIVO
--
-- Errores:
--   LB404 — la multa no existe
--   LB409 — la multa no está en estado PENDIENTE (ya pagada o anulada)
--
-- Tablas afectadas: multas (lectura+UPDATE), prestamos (lectura, para
-- ubicar al usuario dueño de la multa), estados_multa (lectura),
-- usuarios (UPDATE estado condicional), estados_usuario (lectura).
-- ============================================================================
CREATE OR REPLACE FUNCTION sp_pagar_multa(
    p_multa_id BIGINT,
    OUT o_multa_id BIGINT,
    OUT o_usuario_desbloqueado BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_pagada_id          INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
    v_ahora                     TIMESTAMPTZ := NOW();
BEGIN
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
        RAISE EXCEPTION 'La multa % no esta pendiente de pago', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    SELECT id INTO v_estado_pagada_id FROM estados_multa WHERE nombre = 'PAGADA';

    UPDATE multas
       SET estado_multa_id = v_estado_pagada_id,
           fecha_pagada = v_ahora
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
END;
$$;
