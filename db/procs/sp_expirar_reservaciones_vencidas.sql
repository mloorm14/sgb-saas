-- ============================================================================
-- sp_expirar_reservaciones_vencidas
-- Tipo: función (LANGUAGE plpgsql), UPDATE masivo.
--
-- Propósito: pasar a estado EXPIRADA todas las reservaciones que siguen
-- PENDIENTE o LISTA_PARA_RETIRO cuya fecha_limite_retiro ya pasó. Pensada
-- para invocarse periódicamente (job/scheduler del backend).
--
-- Parámetros (nombrados):
--   p_ahora TIMESTAMPTZ DEFAULT NOW() — permite fijar la fecha de referencia
--                                       en pruebas; en producción se usa el
--                                       valor por defecto (NOW()).
--
-- Retorno: INTEGER — cantidad de reservaciones actualizadas.
--
-- Tablas afectadas: reservaciones (lectura+UPDATE), estados_reservacion
-- (lectura).
-- ============================================================================
CREATE OR REPLACE FUNCTION sp_expirar_reservaciones_vencidas(
    p_ahora TIMESTAMPTZ DEFAULT NOW()
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_pendiente_id   INTEGER;
    v_estado_lista_id       INTEGER;
    v_estado_expirada_id    INTEGER;
    v_filas_afectadas       INTEGER;
BEGIN
    SELECT id INTO v_estado_pendiente_id FROM estados_reservacion WHERE nombre = 'PENDIENTE';
    SELECT id INTO v_estado_lista_id     FROM estados_reservacion WHERE nombre = 'LISTA_PARA_RETIRO';
    SELECT id INTO v_estado_expirada_id  FROM estados_reservacion WHERE nombre = 'EXPIRADA';

    UPDATE reservaciones
       SET estado_reservacion_id = v_estado_expirada_id
     WHERE estado_reservacion_id IN (v_estado_pendiente_id, v_estado_lista_id)
       AND fecha_limite_retiro < p_ahora;

    GET DIAGNOSTICS v_filas_afectadas = ROW_COUNT;

    RETURN v_filas_afectadas;
END;
$$;
