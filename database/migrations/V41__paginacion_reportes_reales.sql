-- V41: paginacion real server-side (LIMIT/OFFSET en DB, no slice en frontend)
-- Antes: reportes top-N (libros, morosidad, categorias) usaban LIMIT p_limite DENTRO
-- de la funcion -> wrapper LIMIT/OFFSET externo siempre vacio para offset>0.
-- Se quita el LIMIT interno y se deja ORDER BY; la paginacion la hace el
-- repositorio con wrapper LIMIT :limit OFFSET :offset + COUNT(*) FROM fn(...).
-- Vencidos y uso no tenian LIMIT, pero se añade indice para OFFSET eficiente.
-- Mantiene DEFAULT NULL para compatibilidad con llamadas antiguas (List todo).
-- Nombres descriptivos y funciones pequeñas, una por bloque.

-- ── Morosidad: quitar LIMIT interno ─────────────────
CREATE OR REPLACE FUNCTION fn_reporte_indice_morosidad(p_limite INTEGER DEFAULT NULL)
RETURNS TABLE (usuario_id BIGINT, nombre VARCHAR(100), apellido VARCHAR(100), correo VARCHAR(150), monto_total_adeudado NUMERIC(10,2), cantidad_multas_pendientes BIGINT, dias_atraso_promedio NUMERIC)
LANGUAGE sql STABLE AS $$
    SELECT u.id, u.nombre, u.apellido, u.correo,
           SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
           COUNT(m.id) AS cantidad_multas_pendientes,
           ROUND(AVG(GREATEST(0, EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada)))::NUMERIC),1) AS dias_atraso_promedio
    FROM multas m JOIN estados_multa em ON em.id=m.estado_multa_id
    JOIN prestamos p ON p.id=m.prestamo_id JOIN usuarios u ON u.id=p.usuario_id
    WHERE em.nombre='PENDIENTE' GROUP BY u.id, u.nombre, u.apellido, u.correo
    ORDER BY monto_total_adeudado DESC;
$$;

-- ── Categorias demandadas: quitar LIMIT ───────────
CREATE OR REPLACE FUNCTION fn_reporte_categorias_demandadas(p_limite INTEGER DEFAULT NULL, p_desde TIMESTAMPTZ DEFAULT NULL, p_hasta TIMESTAMPTZ DEFAULT NULL)
RETURNS TABLE (categoria_id INTEGER, categoria_nombre VARCHAR(80), total_prestamos BIGINT, porcentaje NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH total_general AS (SELECT COUNT(*) AS total FROM prestamos pr WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)),
    prestamos_por_categoria AS (
        SELECT c.id AS categoria_id, c.nombre AS categoria_nombre, COUNT(*) AS total_prestamos
        FROM prestamos pr JOIN libros l ON l.id=pr.libro_id JOIN libro_categorias lc ON lc.libro_id=l.id JOIN categorias c ON c.id=lc.categoria_id
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
        GROUP BY c.id, c.nombre)
    SELECT ppc.categoria_id, ppc.categoria_nombre, ppc.total_prestamos,
           ROUND(ppc.total_prestamos * 100.0 / NULLIF(tg.total,0),1) AS porcentaje
    FROM prestamos_por_categoria ppc CROSS JOIN total_general tg
    ORDER BY ppc.total_prestamos DESC;
$$;

-- ── Libros mas prestados detallado: quitar LIMIT ──
-- Nota: V19 se reemplaza sin LIMIT; wrapper repo hara LIMIT/OFFSET
CREATE OR REPLACE FUNCTION fn_reporte_libros_mas_prestados_detallado(p_limite INTEGER DEFAULT NULL, p_desde TIMESTAMPTZ DEFAULT NULL, p_hasta TIMESTAMPTZ DEFAULT NULL, p_categoria_id INTEGER DEFAULT NULL)
RETURNS TABLE (libro_id BIGINT, titulo VARCHAR(255), isbn VARCHAR(13), autor_nombre TEXT, categoria_nombre TEXT, total_prestamos BIGINT, porcentaje NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH total_general AS (SELECT COUNT(*) AS total FROM prestamos pr WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta) AND (p_categoria_id IS NULL OR EXISTS (SELECT 1 FROM libro_categorias lc WHERE lc.libro_id=pr.libro_id AND lc.categoria_id=p_categoria_id))),
    conteo AS (
        SELECT l.id AS libro_id, l.titulo, l.isbn,
               COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor') AS autor_nombre,
               COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoria') AS categoria_nombre,
               COUNT(*) AS total_prestamos
        FROM prestamos pr JOIN libros l ON l.id=pr.libro_id
        LEFT JOIN libro_autores la ON la.libro_id=l.id LEFT JOIN autores a ON a.id=la.autor_id
        LEFT JOIN libro_categorias lc ON lc.libro_id=l.id LEFT JOIN categorias c ON c.id=lc.categoria_id
        WHERE (p_desde IS NULL OR pr.fecha_prestamo >= p_desde) AND (p_hasta IS NULL OR pr.fecha_prestamo <= p_hasta)
          AND (p_categoria_id IS NULL OR EXISTS (SELECT 1 FROM libro_categorias lc2 WHERE lc2.libro_id=pr.libro_id AND lc2.categoria_id=p_categoria_id))
        GROUP BY l.id, l.titulo, l.isbn)
    SELECT c.libro_id, c.titulo, c.isbn, c.autor_nombre, c.categoria_nombre, c.total_prestamos,
           ROUND(c.total_prestamos * 100.0 / NULLIF(tg.total,0),1) AS porcentaje
    FROM conteo c CROSS JOIN total_general tg ORDER BY c.total_prestamos DESC;
$$;

-- ── Indices para paginacion eficiente (vencidos/uso) ─
CREATE INDEX IF NOT EXISTS idx_prestamos_estado_fecha_estimada ON prestamos(estado_prestamo_id, fecha_devolucion_estimada) WHERE fecha_devolucion_real IS NULL;
CREATE INDEX IF NOT EXISTS idx_prestamos_fecha_prestamo ON prestamos(fecha_prestamo);
