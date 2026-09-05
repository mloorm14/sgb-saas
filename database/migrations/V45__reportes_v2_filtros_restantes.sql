-- V45 tanda 2: filtros restantes para reportes gerenciales (legible, 1 responsabilidad por bloque)
-- Libros: ya tiene p_categoria_id (V41), solo documenta exponer en UI
-- Morosidad: añade búsqueda texto y rangos monto/días (HAVING sobre agregados)
-- Vencidos: corrige ISBN ILIKE + añade días max y categoría
-- Categorías: ya tiene desde/hasta, añade búsqueda texto backend

-- Morosidad con búsqueda y rangos
CREATE OR REPLACE FUNCTION fn_reporte_indice_morosidad(
    p_limite INTEGER DEFAULT NULL,
    p_busqueda TEXT DEFAULT NULL,
    p_monto_min NUMERIC DEFAULT NULL,
    p_monto_max NUMERIC DEFAULT NULL,
    p_dias_min NUMERIC DEFAULT NULL,
    p_dias_max NUMERIC DEFAULT NULL
)
RETURNS TABLE (usuario_id BIGINT, nombre VARCHAR(100), apellido VARCHAR(100), correo VARCHAR(150), monto_total_adeudado NUMERIC(10,2), cantidad_multas_pendientes BIGINT, dias_atraso_promedio NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT u.id, u.nombre, u.apellido, u.correo,
           SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
           COUNT(m.id) AS cantidad_multas_pendientes,
           ROUND(AVG(GREATEST(0, EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada)))::NUMERIC),1) AS dias_atraso_promedio
    FROM multas m JOIN estados_multa em ON em.id=m.estado_multa_id
    JOIN prestamos p ON p.id=m.prestamo_id JOIN usuarios u ON u.id=p.usuario_id
    WHERE em.nombre='PENDIENTE'
      AND (p_busqueda IS NULL OR u.correo ILIKE '%' || p_busqueda || '%' OR u.nombre ILIKE '%' || p_busqueda || '%' OR u.apellido ILIKE '%' || p_busqueda || '%')
    GROUP BY u.id, u.nombre, u.apellido, u.correo
    HAVING (p_monto_min IS NULL OR SUM(m.monto) >= p_monto_min)
       AND (p_monto_max IS NULL OR SUM(m.monto) <= p_monto_max)
       AND (p_dias_min IS NULL OR AVG(GREATEST(0, EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada)))) >= p_dias_min)
       AND (p_dias_max IS NULL OR AVG(GREATEST(0, EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada)))) <= p_dias_max)
    ORDER BY monto_total_adeudado DESC;
$$;

-- Vencidos: corrige ISBN + días max
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
