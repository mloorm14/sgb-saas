-- fn_pagos_recientes: últimos N pagos de multas con info de usuario y libro.
CREATE OR REPLACE FUNCTION fn_pagos_recientes(p_limit INTEGER DEFAULT 5)
RETURNS TABLE (
    multa_id BIGINT,
    monto_pagado NUMERIC(8,2),
    fecha_pagada TIMESTAMPTZ,
    usuario_correo VARCHAR(150),
    usuario_nombre VARCHAR(100),
    libro_titulo VARCHAR(200)
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        m.id AS multa_id,
        m.monto_pagado,
        m.fecha_pagada,
        u.correo AS usuario_correo,
        u.nombre || ' ' || u.apellido AS usuario_nombre,
        l.titulo AS libro_titulo
    FROM multas m
    JOIN prestamos p ON p.id = m.prestamo_id
    JOIN usuarios u ON u.id = p.usuario_id
    JOIN libros l ON l.id = p.libro_id
    WHERE m.estado_multa_id = (SELECT id FROM estados_multa WHERE nombre = 'PAGADA')
    ORDER BY m.fecha_pagada DESC
    LIMIT p_limit;
$$;
