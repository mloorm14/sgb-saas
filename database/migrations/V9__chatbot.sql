-- Módulo H (chatbot asistente virtual con Gemini). Tablas de sesiones y
-- mensajes del chat, más la base de conocimiento curada que el prompt de
-- sistema de ChatbotService usa como grounding (el modelo NUNCA inventa
-- datos: responde solo con el contexto real que se le pasa).
CREATE TABLE IF NOT EXISTS sesiones_chat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultima_actividad TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS mensajes_chat (
    id BIGSERIAL PRIMARY KEY,
    sesion_id UUID NOT NULL REFERENCES sesiones_chat(id) ON DELETE CASCADE,
    rol VARCHAR(10) NOT NULL CHECK (rol IN ('USUARIO','ASISTENTE')),
    contenido TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- El historial de una sesión se lee entero por sesión (nunca por usuario en
-- un listado cruzado), así que este índice cubre el único acceso real:
-- findBySesionIdOrderByCreadoEnAsc (MensajeChatRepository).
CREATE INDEX IF NOT EXISTS idx_mensajes_chat_sesion ON mensajes_chat(sesion_id);

CREATE TABLE IF NOT EXISTS base_conocimiento (
    id SERIAL PRIMARY KEY,
    categoria VARCHAR(40) NOT NULL, -- HORARIOS, POLITICAS, MULTAS
    pregunta_ejemplo TEXT NOT NULL,
    respuesta TEXT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT true
);

-- Seed mínimo para que el prompt de sistema tenga contexto real desde el
-- día 1 (BaseConocimientoRepository.findByActivoTrue() lo alimenta).
-- Nota: no se agrega índice pg_trgm aquí — ya existe desde V8
-- (idx_libros_titulo_trgm), esta tabla no hace búsqueda por trigramas.
INSERT INTO base_conocimiento (categoria, pregunta_ejemplo, respuesta) VALUES
('HORARIOS', '¿Cuál es el horario de la biblioteca?', 'La biblioteca atiende de lunes a viernes de 8:00 a 18:00.'),
('POLITICAS', '¿Cuántas veces puedo renovar un préstamo?', 'El número máximo de renovaciones lo define el Gerente en la configuración del sistema; consulta tu préstamo para ver cuántas te quedan.'),
('MULTAS', '¿Qué pasa si devuelvo un libro tarde?', 'Se genera una multa calculada según la tarifa por hora configurada por el sistema; puedes revisarla en la sección de Multas.');
