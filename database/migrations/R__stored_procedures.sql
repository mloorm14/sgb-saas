-- ============================================================================
-- SGB-SaaS — R__stored_procedures.sql
-- Migración REPETIBLE de Flyway (prefijo R__): se re-ejecuta automáticamente
-- en cada deploy cuando cambia su checksum (contenido del archivo), sin
-- necesidad de numerar una versión nueva — el mecanismo estándar de Flyway
-- para funciones/vistas/datos de referencia (la guía de la Entrega Final ya
-- lo prevé: "database/migrations/ # migraciones Flyway V1__, V2__, R__").
--
-- FUENTE CANÓNICA: este archivo es el reflejo versionado de db/procs/*.sql
-- para despliegues sin make up (Render/Neon): db/procs/ (vía
-- scripts/build-init-sql.sh y docker-entrypoint-initdb.d) sigue siendo LA
-- fuente de verdad de los procedimientos en desarrollo local. Si se edita un
-- procedimiento (en db/procs/ o aquí), hay que reflejar el cambio en el otro
-- a mano — no hay generación automática entre ambos todavía.
--
-- Todos los procedimientos usan CREATE OR REPLACE FUNCTION (idempotentes por
-- diseño): re-ejecutar esta migración es siempre un no-op seguro, incluso
-- sobre una base sembrada desde db/procs/ + db/seed.sql (flujo make up).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- sp_crear_prestamo
-- Tipo: función (LANGUAGE plpgsql), no procedure nativo — ver nota de diseño
-- en db/procs/sp_crear_prestamo.sql y docs/basedatos/CATALOGO-SP.md.
-- Retorno: BIGINT — id del préstamo creado.
-- Errores (SQLSTATE personalizado): LB404 (usuario/libro no existen),
-- LB422 (usuario bloqueado por multa, o libro sin stock).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION sp_crear_prestamo(
    p_usuario_id BIGINT,
    p_libro_id BIGINT,
    p_bibliotecario_id BIGINT,
    p_dias_prestamo INTEGER
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_usuario_id     INTEGER;
    v_estado_bloqueado_id   INTEGER;
    v_stock_disponible      SMALLINT;
    v_estado_activo_prestamo_id INTEGER;
    v_prestamo_id           BIGINT;
BEGIN
    SELECT id INTO v_estado_bloqueado_id
      FROM estados_usuario
     WHERE nombre = 'BLOQUEADO_POR_MULTA';

    SELECT estado_id INTO v_estado_usuario_id
      FROM usuarios
     WHERE id = p_usuario_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El usuario % no existe', p_usuario_id USING ERRCODE = 'LB404';
    END IF;

    IF v_estado_usuario_id = v_estado_bloqueado_id THEN
        RAISE EXCEPTION 'El usuario % esta bloqueado por multas pendientes y no puede solicitar prestamos', p_usuario_id
            USING ERRCODE = 'LB422';
    END IF;

    SELECT stock_disponible INTO v_stock_disponible
      FROM libros
     WHERE id = p_libro_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El libro % no existe', p_libro_id USING ERRCODE = 'LB404';
    END IF;

    IF v_stock_disponible <= 0 THEN
        RAISE EXCEPTION 'El libro % no tiene stock disponible', p_libro_id USING ERRCODE = 'LB422';
    END IF;

    SELECT id INTO v_estado_activo_prestamo_id
      FROM estados_prestamo
     WHERE nombre = 'ACTIVO';

    UPDATE libros
       SET stock_disponible = stock_disponible - 1
     WHERE id = p_libro_id;

    INSERT INTO prestamos (
        usuario_id, libro_id, bibliotecario_id,
        fecha_prestamo, fecha_devolucion_estimada, estado_prestamo_id
    ) VALUES (
        p_usuario_id, p_libro_id, p_bibliotecario_id,
        NOW(), NOW() + make_interval(days => p_dias_prestamo), v_estado_activo_prestamo_id
    )
    RETURNING id INTO v_prestamo_id;

    RETURN v_prestamo_id;
END;
$$;

-- ----------------------------------------------------------------------------
-- sp_registrar_devolucion
-- Retorno (OUT): o_prestamo_id BIGINT, o_hubo_multa BOOLEAN,
-- o_monto_multa NUMERIC(8,2).
-- Errores: LB404 (préstamo no existe), LB409 (ya devuelto),
-- LB422 (falta monto_multa_diaria en configuracion_sistema).
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- sp_pagar_multa
-- Retorno (OUT): o_multa_id BIGINT, o_usuario_desbloqueado BOOLEAN.
-- Errores: LB404 (multa no existe), LB409 (no está PENDIENTE).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION sp_pagar_multa(
    p_multa_id BIGINT,
    OUT o_multa_id BIGINT,
    OUT o_usuario_desbloqueado BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_pagada_id          INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
    v_ahora                     TIMESTAMPTZ := NOW();
BEGIN
    SELECT m.estado_multa_id, p.usuario_id
      INTO v_estado_multa_id, v_usuario_id
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

    SELECT id INTO v_estado_pagada_id FROM estados_multa WHERE nombre = 'PAGADA';

    UPDATE multas
       SET estado_multa_id = v_estado_pagada_id,
           fecha_pagada = v_ahora
     WHERE id = p_multa_id;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;
END;
$$;

-- ----------------------------------------------------------------------------
-- sp_anular_multa
-- Requiere rol GERENTE o ADMIN (segunda barrera además del @PreAuthorize).
-- Retorno (OUT): o_multa_id BIGINT, o_usuario_desbloqueado BOOLEAN.
-- Errores: LB422 (rol inválido), LB404 (multa no existe), LB409 (no PENDIENTE).
-- Deja constancia en bitacora_auditoria (usuario_id NULL: la función solo
-- recibe el rol, no el id del ejecutor — ver nota en db/procs/sp_anular_multa.sql).
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION sp_anular_multa(
    p_multa_id BIGINT,
    p_motivo VARCHAR(255),
    p_rol_ejecutor VARCHAR(30),
    OUT o_multa_id BIGINT,
    OUT o_usuario_desbloqueado BOOLEAN
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado_multa_id           INTEGER;
    v_estado_pendiente_id       INTEGER;
    v_estado_anulada_id         INTEGER;
    v_estado_activo_usuario_id  INTEGER;
    v_usuario_id                BIGINT;
    v_otras_pendientes          INTEGER;
BEGIN
    IF p_rol_ejecutor <> 'GERENTE' AND p_rol_ejecutor <> 'ADMIN' THEN
        RAISE EXCEPTION 'Solo GERENTE o ADMIN puede anular multas' USING ERRCODE = 'LB422';
    END IF;

    SELECT m.estado_multa_id, p.usuario_id
      INTO v_estado_multa_id, v_usuario_id
      FROM multas m
      JOIN prestamos p ON p.id = m.prestamo_id
     WHERE m.id = p_multa_id
     FOR UPDATE OF m;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'La multa % no existe', p_multa_id USING ERRCODE = 'LB404';
    END IF;

    SELECT id INTO v_estado_pendiente_id FROM estados_multa WHERE nombre = 'PENDIENTE';

    IF v_estado_multa_id <> v_estado_pendiente_id THEN
        RAISE EXCEPTION 'La multa % no esta pendiente, no se puede anular', p_multa_id USING ERRCODE = 'LB409';
    END IF;

    SELECT id INTO v_estado_anulada_id FROM estados_multa WHERE nombre = 'ANULADA';

    UPDATE multas
       SET estado_multa_id = v_estado_anulada_id,
           observaciones = p_motivo
     WHERE id = p_multa_id;

    o_multa_id := p_multa_id;

    SELECT count(*) INTO v_otras_pendientes
      FROM multas m2
      JOIN prestamos p2 ON p2.id = m2.prestamo_id
     WHERE p2.usuario_id = v_usuario_id
       AND m2.estado_multa_id = v_estado_pendiente_id
       AND m2.id <> p_multa_id;

    IF v_otras_pendientes = 0 THEN
        SELECT id INTO v_estado_activo_usuario_id FROM estados_usuario WHERE nombre = 'ACTIVO';
        UPDATE usuarios SET estado_id = v_estado_activo_usuario_id WHERE id = v_usuario_id;
        o_usuario_desbloqueado := TRUE;
    ELSE
        o_usuario_desbloqueado := FALSE;
    END IF;

    INSERT INTO bitacora_auditoria (usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles)
    VALUES (NULL, 'UPDATE', 'multas', p_multa_id, 'Multa anulada: ' || p_motivo);
END;
$$;

-- ----------------------------------------------------------------------------
-- sp_expirar_reservaciones_vencidas
-- Pasa a EXPIRADA las reservaciones PENDIENTE/LISTA_PARA_RETIRO cuya
-- fecha_limite_retiro ya pasó. Retorno: INTEGER (filas actualizadas).
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- fn_reporte_uso_por_periodo
-- REPORTE gerencial de uso (RF-12): préstamos y devoluciones agrupados por
-- día/semana/mes (FULL OUTER JOIN entre ambas agregaciones).
-- Parámetros: p_granularidad TEXT DEFAULT 'dia' ('dia'|'semana'|'mes'),
-- p_desde/p_hasta TIMESTAMPTZ (NULL = sin límite). Retorno TABLE.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_uso_por_periodo(
    p_granularidad TEXT DEFAULT 'dia',
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    periodo             TIMESTAMPTZ,
    total_prestamos      BIGINT,
    total_devoluciones    BIGINT
)
LANGUAGE sql
STABLE
AS $$
    WITH campo AS (
        SELECT CASE p_granularidad
                   WHEN 'semana' THEN 'week'
                   WHEN 'mes'    THEN 'month'
                   ELSE 'day'
               END AS valor
    ),
    prestamos_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_prestamo) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
        GROUP BY 1
    ),
    devoluciones_agg AS (
        SELECT
            date_trunc((SELECT valor FROM campo), p.fecha_devolucion_real) AS periodo,
            COUNT(*) AS total
        FROM prestamos p
        WHERE p.fecha_devolucion_real IS NOT NULL
          AND (p_desde IS NULL OR p.fecha_devolucion_real >= p_desde)
          AND (p_hasta IS NULL OR p.fecha_devolucion_real <= p_hasta)
        GROUP BY 1
    )
    SELECT
        COALESCE(pa.periodo, da.periodo) AS periodo,
        COALESCE(pa.total, 0) AS total_prestamos,
        COALESCE(da.total, 0) AS total_devoluciones
    FROM prestamos_agg pa
    FULL OUTER JOIN devoluciones_agg da ON da.periodo = pa.periodo
    ORDER BY periodo;
$$;

-- ----------------------------------------------------------------------------
-- fn_reporte_libros_mas_prestados
-- REPORTE de los libros con más préstamos (RF-12), opcionalmente acotado a
-- un rango de fechas. Parámetros: p_limite INTEGER DEFAULT 10,
-- p_desde/p_hasta TIMESTAMPTZ (NULL = sin límite). Retorno TABLE.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_libros_mas_prestados(
    p_limite INTEGER DEFAULT 10,
    p_desde TIMESTAMPTZ DEFAULT NULL,
    p_hasta TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    libro_id         BIGINT,
    titulo           VARCHAR(255),
    isbn             VARCHAR(13),
    total_prestamos  BIGINT
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        l.id,
        l.titulo,
        l.isbn,
        COUNT(*) AS total_prestamos
    FROM prestamos p
    JOIN libros l ON l.id = p.libro_id
    WHERE (p_desde IS NULL OR p.fecha_prestamo >= p_desde)
      AND (p_hasta IS NULL OR p.fecha_prestamo <= p_hasta)
    GROUP BY l.id, l.titulo, l.isbn
    ORDER BY total_prestamos DESC
    LIMIT p_limite;
$$;

-- ----------------------------------------------------------------------------
-- fn_reporte_indice_morosidad
-- REPORTE de morosidad (RF-12): usuarios con multas PENDIENTES, monto total
-- adeudado y días de atraso promedio de los préstamos que las originaron
-- (GREATEST(0, ...) evita días negativos en multas por daño). Parámetros:
-- p_limite INTEGER DEFAULT 10. Retorno TABLE.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_indice_morosidad(
    p_limite INTEGER DEFAULT 10
)
RETURNS TABLE (
    usuario_id                  BIGINT,
    nombre                       VARCHAR(100),
    apellido                     VARCHAR(100),
    correo                       VARCHAR(150),
    monto_total_adeudado         NUMERIC(10,2),
    cantidad_multas_pendientes   BIGINT,
    dias_atraso_promedio         NUMERIC
)
LANGUAGE sql
STABLE
AS $$
    SELECT
        u.id,
        u.nombre,
        u.apellido,
        u.correo,
        SUM(m.monto)::NUMERIC(10,2) AS monto_total_adeudado,
        COUNT(m.id) AS cantidad_multas_pendientes,
        ROUND(
            AVG(
                GREATEST(
                    0,
                    EXTRACT(DAY FROM (COALESCE(p.fecha_devolucion_real, NOW()) - p.fecha_devolucion_estimada))
                )
            )::NUMERIC,
            1
        ) AS dias_atraso_promedio
    FROM multas m
    JOIN estados_multa em ON em.id = m.estado_multa_id
    JOIN prestamos p ON p.id = m.prestamo_id
    JOIN usuarios u ON u.id = p.usuario_id
    WHERE em.nombre = 'PENDIENTE'
    GROUP BY u.id, u.nombre, u.apellido, u.correo
    ORDER BY monto_total_adeudado DESC
    LIMIT p_limite;
$$;

-- ----------------------------------------------------------------------------
-- fn_listar_prestamos_activos_por_usuario
-- Proyección heterogénea (préstamo + libro + estado) de los préstamos
-- "activos" (todo estado ≠ 'DEVUELTO') de un usuario. Parámetros:
-- p_usuario_id BIGINT. Retorno TABLE.
-- ----------------------------------------------------------------------------
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