-- Modulo 2: notificaciones por correo (alertas de vencimiento, multa y
-- reserva caducada). tipos_notificacion es un catalogo nuevo (no existia en
-- el schema base), asi que sus valores se siembran aqui con ON CONFLICT DO
-- NOTHING -- mismo criterio que V4__configuracion_sistema_valores_iniciales.sql:
-- db/seed.sql solo corre en la creacion inicial de la base
-- (docker-entrypoint-initdb.d), nunca en un ambiente ya existente que
-- aplica esta migracion via Flyway.
CREATE TABLE IF NOT EXISTS tipos_notificacion (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(30) UNIQUE NOT NULL
);

INSERT INTO tipos_notificacion (nombre) VALUES
    ('VENCIMIENTO'),
    ('MULTA'),
    ('RESERVA_CADUCADA')
ON CONFLICT (nombre) DO NOTHING;

-- prestamo_id es NULLABLE a proposito: una notificacion de tipo MULTA por
-- dano al libro o RESERVA_CADUCADA no siempre tiene un prestamo de origen
-- (ver Multa/Reservacion, que tampoco llevan referencia obligatoria entre
-- si). Sin FK a tipos_notificacion como ON DELETE CASCADE -- un catalogo no
-- se borra en producción, y si algun dia se borra una fila de el, se
-- prefiere que falle explicito (RESTRICT, el default) antes que arrastrar
-- notificaciones historicas en silencio.
CREATE TABLE IF NOT EXISTS notificaciones (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    prestamo_id          BIGINT REFERENCES prestamos(id) ON DELETE SET NULL,
    tipo_notificacion_id INTEGER NOT NULL REFERENCES tipos_notificacion(id) ON DELETE RESTRICT,
    mensaje              TEXT NOT NULL,
    fecha_envio          TIMESTAMPTZ,
    enviado_ok           BOOLEAN NOT NULL DEFAULT FALSE,
    error_envio          VARCHAR(255),
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Usada por NotificacionController (GET /notificaciones/usuario/{id}) y por
-- el scheduler de vencimiento para evitar alertas duplicadas sobre el mismo
-- prestamo.
CREATE INDEX IF NOT EXISTS idx_notificaciones_usuario ON notificaciones (usuario_id);
