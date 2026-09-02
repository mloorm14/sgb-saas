-- Suscripciones manuales "Notificarme cuando este disponible" (catalogo lector).
-- Un usuario puede suscribirse a multiples libros; una fila por par usuario-libro.
CREATE TABLE IF NOT EXISTS suscripciones_disponibilidad (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    libro_id   BIGINT NOT NULL REFERENCES libros(id) ON DELETE CASCADE,
    creado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(usuario_id, libro_id)
);
CREATE INDEX IF NOT EXISTS idx_susc_usuario ON suscripciones_disponibilidad(usuario_id);
CREATE INDEX IF NOT EXISTS idx_susc_libro ON suscripciones_disponibilidad(libro_id);

-- Tipo de notificacion para libro disponible (manual, no automatico periodico)
INSERT INTO tipos_notificacion (nombre) VALUES ('DISPONIBLE')
ON CONFLICT (nombre) DO NOTHING;
