-- Búsqueda predictiva / autocompletado de catálogo (Módulo 3 del roadmap,
-- RF-09 / CU-08). pg_trgm habilita similarity(texto, texto) y el operador
-- de coincidencia por trigramas, usado por LibroRepository.sugerirPorTitulo()
-- para tolerar errores de tipeo/orden parcial en vez de exigir un LIKE
-- exacto por prefijo.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Índice GIN sobre libros.titulo: sin esto, cada tecla presionada en el
-- autocompletado del frontend dispararía un seq scan completo de la tabla
-- libros. gin_trgm_ops es el operator class que pg_trgm expone para que
-- el índice soporte tanto ILIKE '%texto%' como similarity(titulo, :texto).
CREATE INDEX IF NOT EXISTS idx_libros_titulo_trgm
    ON libros USING gin (titulo gin_trgm_ops);
