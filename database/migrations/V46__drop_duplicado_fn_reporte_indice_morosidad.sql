-- V46: drop duplicado fn_reporte_indice_morosidad que causa 42725 function is not unique
-- V45 añade overload con 6 params (p_limite, p_busqueda, p_monto_min/max, p_dias_min/max) con DEFAULT NULL
-- R__/db/procs mantiene overload simple fn(p_limite integer) con LIMIT COALESCE(p_limite,10)
-- Ambos con DEFAULT permiten llamada con 1 arg -> Postgres no puede elegir -> 500 en GET /api/v1/prestamos/reportes/morosidad
-- Se dropea el overload filtrado (no usado aún por PrestamoProcedureRepository que llama fn(:p_limite) single param)
-- Frontend morosidad actual no envía filtros, usa wrapper LIMIT/OFFSET externo (V41). El filtrado se reintroducirá
-- con nombre distinto o repo explícito cuando UI lo requiera.
DROP FUNCTION IF EXISTS fn_reporte_indice_morosidad(integer, text, numeric, numeric, numeric, numeric) CASCADE;
-- Reasegurar overload simple con LIMIT COALESCE (idempotente, por si R__ no se aplicó)
CREATE OR REPLACE FUNCTION fn_reporte_indice_morosidad(p_limite INTEGER DEFAULT 10)
RETURNS TABLE (usuario_id BIGINT, nombre VARCHAR(100), apellido VARCHAR(100), correo VARCHAR(150), monto_total_adeudado NUMERIC(10,2), cantidad_multas_pendientes BIGINT, dias_atraso_promedio NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT u.id, u.nombre, u.apellido, u.correo,
           SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
           COUNT(m.id) AS cantidad_multas_pendientes,
           ROUND(AVG(GREATEST(0, EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada)))::NUMERIC),1) AS dias_atraso_promedio
    FROM multas m JOIN estados_multa em ON em.id=m.estado_multa_id
    JOIN prestamos p ON p.id=m.prestamo_id JOIN usuarios u ON u.id=p.usuario_id
    WHERE em.nombre='PENDIENTE' GROUP BY u.id, u.nombre, u.apellido, u.correo
    ORDER BY monto_total_adeudado DESC LIMIT COALESCE(p_limite,10);
$$;
