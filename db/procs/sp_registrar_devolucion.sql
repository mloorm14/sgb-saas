-- ============================================================================
-- sp_registrar_devolucion
-- Tipo: función (LANGUAGE plpgsql), múltiples parámetros de salida (OUT).
--
-- Propósito: registrar la devolución de un préstamo. Si la devolución es
-- tardía, genera una multa automáticamente y bloquea al usuario.
--
-- Parámetros (nombrados):
--   p_prestamo_id BIGINT — préstamo a devolver
--
-- Retorno (OUT, fila única — ver nota de integración en CATALOGO-SP.md sobre
-- @NamedStoredProcedureQuery con múltiples parámetros OUT):
--   o_prestamo_id  BIGINT   — mismo id recibido, para confirmación
--   o_hubo_multa   BOOLEAN  — si se generó una multa por atraso
--   o_monto_multa  NUMERIC(8,2) — monto de la multa generada, NULL si no hubo
--
-- Errores:
--   LB404 — el préstamo no existe
--   LB409 — el préstamo ya estaba devuelto (evita doble devolución)
--   LB422 — falta configurar 'monto_multa_diaria' en configuracion_sistema
--
-- Tablas afectadas: prestamos (lectura+UPDATE), libros (UPDATE stock),
-- estados_prestamo (lectura), configuracion_sistema (lectura),
-- estados_multa (lectura), multas (INSERT si hay atraso),
-- estados_usuario (lectura), usuarios (UPDATE estado si hay atraso).
-- ============================================================================
CREATE OR REPLACE FUNCTION sp_registrar_devolucion(
    p_prestamo_id BIGINT,
    OUT o_prestamo_id BIGINT,
    OUT o_hubo_multa BOOLEAN,
    OUT o_monto_multa NUMERIC(8,2)
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_libro_id                    BIGINT;
    v_usuario_id                  BIGINT;
    v_estado_prestamo_id          INTEGER;
    v_estado_devuelto_id          INTEGER;
    v_fecha_devolucion_estimada   TIMESTAMPTZ;
    v_dias_atraso                 INTEGER;
    v_valor_multa_diaria          NUMERIC(8,2);
    v_estado_pendiente_multa_id   INTEGER;
    v_estado_bloqueado_id         INTEGER;
    v_ahora                       TIMESTAMPTZ := NOW();
BEGIN
    SELECT libro_id, usuario_id, estado_prestamo_id, fecha_devolucion_estimada
      INTO v_libro_id, v_usuario_id, v_estado_prestamo_id, v_fecha_devolucion_estimada
      FROM prestamos
     WHERE id = p_prestamo_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El prestamo % no existe', p_prestamo_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_devuelto_id FROM estados_prestamo WHERE nombre = 'DEVUELTO';

    IF v_estado_prestamo_id = v_estado_devuelto_id THEN
        RAISE EXCEPTION 'El prestamo % ya fue devuelto', p_prestamo_id USING ERRCODE = 'LB409';
    END IF;

    UPDATE prestamos
       SET fecha_devolucion_real = v_ahora,
           estado_prestamo_id = v_estado_devuelto_id
     WHERE id = p_prestamo_id;

    UPDATE libros
       SET stock_disponible = stock_disponible + 1
     WHERE id = v_libro_id;

    o_prestamo_id := p_prestamo_id;
    o_hubo_multa := FALSE;
    o_monto_multa := NULL;

    IF v_ahora > v_fecha_devolucion_estimada THEN
        -- Cualquier atraso, aunque sea de horas, cuenta como mínimo 1 día.
        v_dias_atraso := CEIL(EXTRACT(EPOCH FROM (v_ahora - v_fecha_devolucion_estimada)) / 86400.0)::INTEGER;

        SELECT valor::NUMERIC INTO v_valor_multa_diaria
          FROM configuracion_sistema
         WHERE clave = 'monto_multa_diaria';

        IF v_valor_multa_diaria IS NULL THEN
            RAISE EXCEPTION 'Falta configurar monto_multa_diaria en configuracion_sistema'
                USING ERRCODE = 'LB422';
        END IF;

        o_monto_multa := v_dias_atraso * v_valor_multa_diaria;
        o_hubo_multa := TRUE;

        SELECT id INTO v_estado_pendiente_multa_id FROM estados_multa WHERE nombre = 'PENDIENTE';

        INSERT INTO multas (prestamo_id, monto, estado_multa_id, fecha_generada)
        VALUES (p_prestamo_id, o_monto_multa, v_estado_pendiente_multa_id, v_ahora);

        SELECT id INTO v_estado_bloqueado_id FROM estados_usuario WHERE nombre = 'BLOQUEADO_POR_MULTA';

        UPDATE usuarios SET estado_id = v_estado_bloqueado_id WHERE id = v_usuario_id;
    END IF;
END;
$$;
