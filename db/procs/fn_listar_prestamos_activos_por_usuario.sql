-- ============================================================================
-- fn_listar_prestamos_activos_por_usuario
-- Tipo: función SQL pura (LANGUAGE sql, STABLE), retorna TABLE (varias filas).
--
-- Propósito: proyección heterogénea (préstamo + libro + estado) de los
-- préstamos "activos" de un usuario. Se define aquí porque requiere JOIN
-- entre prestamos/libros/estados_prestamo — no es CRUD elemental.
--
-- Definición de "activo": cualquier préstamo cuyo estado NO sea 'DEVUELTO'
-- (incluye ACTIVO, RENOVADO y VENCIDO), para que un lector vea también los
-- libros que tiene atrasados. Si se requiere restringir estrictamente a
-- estado_prestamo = 'ACTIVO', ajustar el filtro WHERE de abajo.
--
-- Parámetros (nombrados):
--   p_usuario_id BIGINT — usuario cuyos préstamos se listan
--
-- Retorno TABLE:
--   prestamo_id                 BIGINT
--   libro_titulo                VARCHAR(255)
--   libro_isbn                  VARCHAR(13)
--   fecha_prestamo               TIMESTAMPTZ
--   fecha_devolucion_estimada    TIMESTAMPTZ
--   dias_restantes                INTEGER  -- negativo si está atrasado
--   estado_nombre                VARCHAR(30)
--
-- Tablas afectadas: solo lectura (prestamos, libros, estados_prestamo).
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_listar_prestamos_activos_por_usuario(
    p_usuario_id BIGINT
)
RETURNS TABLE (
    prestamo_id                BIGINT,
    libro_titulo               VARCHAR(255),
    libro_isbn                 VARCHAR(13),
    fecha_prestamo              TIMESTAMPTZ,
    fecha_devolucion_estimada   TIMESTAMPTZ,
    dias_restantes               INTEGER,
    estado_nombre                VARCHAR(30)
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        p.id,
        l.titulo,
        l.isbn,
        p.fecha_prestamo,
        p.fecha_devolucion_estimada,
        (p.fecha_devolucion_estimada::date - NOW()::date)::INTEGER,
        ep.nombre
    FROM prestamos p
    JOIN libros l ON l.id = p.libro_id
    JOIN estados_prestamo ep ON ep.id = p.estado_prestamo_id
    WHERE p.usuario_id = p_usuario_id
      AND ep.nombre <> 'DEVUELTO'
    ORDER BY p.fecha_devolucion_estimada ASC;
$$;
