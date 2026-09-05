-- V44 tanda 1: inventario v2 — 8 filtros gerenciales (categoria ya existía, ahora editorial/año/stock/ubicación/proveedor/estado/idioma)
-- Mantiene DEFAULT NULL para compatibilidad; wrapper repo hace LIMIT/OFFSET + COUNT.
CREATE OR REPLACE FUNCTION fn_reporte_inventario(
    p_categoria_id INTEGER DEFAULT NULL,
    p_estado_stock TEXT DEFAULT NULL,
    p_busqueda TEXT DEFAULT NULL,
    p_editorial_id INTEGER DEFAULT NULL,
    p_proveedor_id INTEGER DEFAULT NULL,
    p_estado_libro_id INTEGER DEFAULT NULL,
    p_idioma_id INTEGER DEFAULT NULL,
    p_anio_desde SMALLINT DEFAULT NULL,
    p_anio_hasta SMALLINT DEFAULT NULL,
    p_stock_total_min SMALLINT DEFAULT NULL,
    p_stock_total_max SMALLINT DEFAULT NULL,
    p_stock_disp_min SMALLINT DEFAULT NULL,
    p_stock_disp_max SMALLINT DEFAULT NULL,
    p_ubicacion TEXT DEFAULT NULL
)
RETURNS TABLE (
    libro_id BIGINT, titulo VARCHAR(255), isbn VARCHAR(13),
    autor_nombre TEXT, categoria_nombre TEXT,
    editorial_nombre TEXT, proveedor_nombre TEXT,
    idioma_nombre TEXT, estado_libro_nombre TEXT,
    anio_publicacion SMALLINT, ubicacion_fisica VARCHAR(50),
    stock_total SMALLINT, stock_disponible SMALLINT, estado_disponibilidad TEXT
)
LANGUAGE sql STABLE AS $$
    SELECT l.id, l.titulo, l.isbn,
           COALESCE(string_agg(DISTINCT a.nombre, ', ' ORDER BY a.nombre), 'Sin autor'),
           COALESCE(string_agg(DISTINCT c.nombre, ', ' ORDER BY c.nombre), 'Sin categoría'),
           e.nombre, prov.nombre, idi.nombre, el.nombre,
           l.anio_publicacion, l.ubicacion_fisica,
           l.stock_total, l.stock_disponible,
           CASE WHEN l.stock_disponible=0 THEN 'Agotado' WHEN l.stock_disponible=1 THEN 'Baja disponibilidad' ELSE 'Disponible' END
    FROM libros l
    LEFT JOIN libro_autores la ON la.libro_id=l.id LEFT JOIN autores a ON a.id=la.autor_id
    LEFT JOIN libro_categorias lc ON lc.libro_id=l.id LEFT JOIN categorias c ON c.id=lc.categoria_id
    LEFT JOIN editoriales e ON e.id=l.editorial_id
    LEFT JOIN proveedores prov ON prov.id=l.proveedor_id
    LEFT JOIN idiomas idi ON idi.id=l.idioma_id
    LEFT JOIN estados_libro el ON el.id=l.estado_id
    WHERE (p_categoria_id IS NULL OR lc.categoria_id=p_categoria_id)
      AND (p_editorial_id IS NULL OR l.editorial_id=p_editorial_id)
      AND (p_proveedor_id IS NULL OR l.proveedor_id=p_proveedor_id)
      AND (p_estado_libro_id IS NULL OR l.estado_id=p_estado_libro_id)
      AND (p_idioma_id IS NULL OR l.idioma_id=p_idioma_id)
      AND (p_anio_desde IS NULL OR l.anio_publicacion >= p_anio_desde)
      AND (p_anio_hasta IS NULL OR l.anio_publicacion <= p_anio_hasta)
      AND (p_stock_total_min IS NULL OR l.stock_total >= p_stock_total_min)
      AND (p_stock_total_max IS NULL OR l.stock_total <= p_stock_total_max)
      AND (p_stock_disp_min IS NULL OR l.stock_disponible >= p_stock_disp_min)
      AND (p_stock_disp_max IS NULL OR l.stock_disponible <= p_stock_disp_max)
      AND (p_ubicacion IS NULL OR l.ubicacion_fisica ILIKE '%' || p_ubicacion || '%')
      AND (p_busqueda IS NULL OR l.titulo ILIKE '%' || p_busqueda || '%'
           OR l.isbn ILIKE '%' || p_busqueda || '%' OR a.nombre ILIKE '%' || p_busqueda || '%'
           OR e.nombre ILIKE '%' || p_busqueda || '%' OR prov.nombre ILIKE '%' || p_busqueda || '%')
    GROUP BY l.id, l.titulo, l.isbn, l.stock_total, l.stock_disponible,
             e.nombre, prov.nombre, idi.nombre, el.nombre, l.anio_publicacion, l.ubicacion_fisica
    HAVING (p_estado_stock IS NULL
            OR (p_estado_stock='agotado' AND l.stock_disponible=0)
            OR (p_estado_stock='baja' AND l.stock_disponible=1)
            OR (p_estado_stock='disponible' AND l.stock_disponible>1))
    ORDER BY l.stock_disponible ASC, l.titulo ASC;
$$;

CREATE INDEX IF NOT EXISTS idx_libros_editorial_stock ON libros(editorial_id, stock_disponible);
CREATE INDEX IF NOT EXISTS idx_libros_anio ON libros(anio_publicacion);
CREATE INDEX IF NOT EXISTS idx_libros_ubicacion_trgm ON libros USING gin (ubicacion_fisica gin_trgm_ops);
