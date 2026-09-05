-- V47: fix fn_reporte_prestamos_vencidos overload is not unique (Position 15)
-- V21 define fn(p_dias_atraso_min INTEGER, p_busqueda TEXT)
-- V45 define fn(p_dias_atraso_min INTEGER, p_busqueda TEXT, p_dias_atraso_max INTEGER)
-- Ambos con DEFAULT NULL en los 3 params hacen que llamada con 2 args sea ambigua -> 500 en Reportes gerenciales
-- Se dropea la firma vieja de 2 params y se reasegura la de 3 params con filtros completos (incluye ISBN).
DROP FUNCTION IF EXISTS fn_reporte_prestamos_vencidos(INTEGER, TEXT) CASCADE;
DROP FUNCTION IF EXISTS fn_reporte_prestamos_vencidos(INTEGER, TEXT, INTEGER) CASCADE;
CREATE OR REPLACE FUNCTION fn_reporte_prestamos_vencidos(
    p_dias_atraso_min INTEGER DEFAULT NULL,
    p_busqueda TEXT DEFAULT NULL,
    p_dias_atraso_max INTEGER DEFAULT NULL
)
RETURNS TABLE (prestamo_id BIGINT, usuario_nombre TEXT, usuario_correo VARCHAR(150), libro_titulo VARCHAR(255), libro_isbn VARCHAR(13), fecha_devolucion_estimada TIMESTAMPTZ, dias_atraso BIGINT, monto_multa_estimada NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT p.id, u.nombre || ' ' || u.apellido, u.correo, l.titulo, l.isbn, p.fecha_devolucion_estimada,
           EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada)::BIGINT AS dias_atraso,
           (EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) * COALESCE((SELECT valor::NUMERIC FROM configuracion_sistema WHERE clave='monto_multa_diaria'), 1))::NUMERIC AS monto_multa_estimada
    FROM prestamos p JOIN usuarios u ON u.id=p.usuario_id JOIN libros l ON l.id=p.libro_id
    JOIN estados_prestamo ep ON ep.id=p.estado_prestamo_id
    WHERE ep.nombre IN ('ACTIVO','RENOVADO') AND p.fecha_devolucion_estimada < NOW()
      AND (p_dias_atraso_min IS NULL OR EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) >= p_dias_atraso_min)
      AND (p_dias_atraso_max IS NULL OR EXTRACT(DAY FROM NOW() - p.fecha_devolucion_estimada) <= p_dias_atraso_max)
      AND (p_busqueda IS NULL OR u.nombre ILIKE '%' || p_busqueda || '%' OR u.correo ILIKE '%' || p_busqueda || '%' OR l.titulo ILIKE '%' || p_busqueda || '%' OR l.isbn ILIKE '%' || p_busqueda || '%')
    ORDER BY dias_atraso DESC;
$$;
