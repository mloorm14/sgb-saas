-- V16: Soporte para pagos parciales de multas
-- Agrega columna monto_pagado, SP de pago parcial, y tipo de notificación
-- para comprobantes de pago enviados por correo.

-- ── Columna monto_pagado ─────────────────────────────────────────────
-- Default 0: multas existentes quedan como "sin pago registrado".
-- NUMERIC(8,2): mismo tipo que monto, soporta decimales (hasta $999999.99).
ALTER TABLE multas ADD COLUMN monto_pagado NUMERIC(8,2) NOT NULL DEFAULT 0;

-- ── SP sp_pago_parcial_multa ─────────────────────────────────────────
-- Acepta un monto parcial, lo acumula en monto_pagado.
-- Si monto_pagado >= monto → marca PAGADA y fecha_pagada.
-- Si monto_pagado < monto  → mantiene PENDIENTE.
-- Retorna: o_multa_id, o_estado ('PAGADA'|'PENDIENTE'),
--          o_saldo_restante, o_usuario_desbloqueado.
CREATE OR REPLACE FUNCTION sp_pago_parcial_multa(
    p_multa_id BIGINT,
    p_monto_pagado NUMERIC(8,2),
    OUT o_multa_id BIGINT,
    OUT o_estado VARCHAR(20),
    OUT o_saldo_restante NUMERIC(8,2),
    OUT o_usuario_desbloqueado BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_pagada_id          INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_monto_total               NUMERIC(8,2);
    v_monto_pagado_actual       NUMERIC(8,2);
    v_usuario_id                BIGINT;
    v_nuevo_pagado              NUMERIC(8,2);
    v_otras_pendientes          INTEGER;
    v_ahora                     TIMESTAMPTZ := NOW();
BEGIN
    SELECT m.estado_multa_id, m.monto, m.monto_pagado, p.usuario_id
      INTO v_estado_multa_id, v_monto_total, v_monto_pagado_actual, v_usuario_id
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

    IF p_monto_pagado IS NULL OR p_monto_pagado <= 0 THEN
        RAISE EXCEPTION 'El monto a pagar debe ser mayor a cero' USING ERRCODE = 'LB400';
    END IF;

    v_nuevo_pagado := v_monto_pagado_actual + p_monto_pagado;

    SELECT id INTO v_estado_pagada_id FROM estados_multa WHERE nombre = 'PAGADA';

    IF v_nuevo_pagado >= v_monto_total THEN
        UPDATE multas
           SET monto_pagado = v_monto_total,
               estado_multa_id = v_estado_pagada_id,
               fecha_pagada = v_ahora
         WHERE id = p_multa_id;
        o_estado := 'PAGADA';
        o_saldo_restante := 0;
    ELSE
        UPDATE multas
           SET monto_pagado = v_nuevo_pagado
         WHERE id = p_multa_id;
        o_estado := 'PENDIENTE';
        o_saldo_restante := v_monto_total - v_nuevo_pagado;
    END IF;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF o_estado = 'PAGADA' AND v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;
END;
$$;

-- ── Tipo de notificación: comprobante de pago ────────────────────────
INSERT INTO tipos_notificacion (nombre) VALUES ('COMPROBANTE_PAGO')
ON CONFLICT DO NOTHING;
