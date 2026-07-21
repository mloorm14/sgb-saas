-- ============================================================================
-- sp_crear_prestamo
-- Tipo: función (LANGUAGE plpgsql), no procedure nativo — ver nota de diseño
-- en docs/basedatos/CATALOGO-SP.md sobre por qué se usa FUNCTION en todos
-- los casos de este módulo.
--
-- Propósito: registrar un nuevo préstamo de forma atómica, validando que el
-- usuario no esté bloqueado por multas y que el libro tenga stock disponible.
--
-- Parámetros (nombrados):
--   p_usuario_id       BIGINT  — usuario que retira el libro
--   p_libro_id         BIGINT  — libro a prestar
--   p_bibliotecario_id BIGINT  — usuario (rol BIBLIOTECARIO/GERENTE/ADMIN)
--                                que registra la operación
--   p_dias_prestamo    INTEGER — plazo del préstamo en días
--
-- Retorno: BIGINT — id del préstamo creado.
--
-- Errores (SQLSTATE personalizado, ver convención en CATALOGO-SP.md):
--   LB404 — usuario o libro no existen
--   LB422 — usuario bloqueado por multa, o libro sin stock disponible
--
-- Tablas afectadas: usuarios (lectura), libros (lectura+UPDATE stock),
-- estados_usuario (lectura), estados_prestamo (lectura), prestamos (INSERT).
--
-- Toda la lógica corre en una única transacción implícita de la función:
-- si cualquier RAISE EXCEPTION dispara, PostgreSQL revierte automáticamente
-- todos los cambios hechos dentro de esta invocación.
-- ============================================================================
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
