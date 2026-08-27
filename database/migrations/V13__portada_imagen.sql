-- Portada del libro como imagen binaria en la propia base de datos.
--
-- Contexto: la tabla libros solo tenia portada_url VARCHAR(1000), un texto
-- de URL a un servidor externo que el sistema no controla (el seed la deja
-- en NULL; no existia ningun endpoint de subida de archivos entre los 45
-- endpoints actuales). Si ese host externo cae, cambia o se borra, la
-- portada se pierde sin que la base de datos pueda recuperarla. Estas
-- columnas nuevas guardan el binario (BYTEA) y su metadata dentro de la
-- propia BD, como corresponde a un sistema con persistencia propia.
--
-- portada_url NO se toca ni se borra: queda como fallback para libros
-- existentes y como dato legacy; al subir una portada nueva el backend
-- limpia ese campo a NULL (ver LibroService.actualizarPortada).
--
-- IF NOT EXISTS: mismo criterio que el resto de migraciones (V4+, corren
-- sobre bases ya creadas desde db/schema.sql con baseline-on-migrate).
ALTER TABLE libros
    ADD COLUMN IF NOT EXISTS portada_imagen  BYTEA,
    ADD COLUMN IF NOT EXISTS portada_nombre  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS portada_tipo    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS portada_tamanio INTEGER;

-- Limite de tamano de portada en megabytes (negocio, validado en
-- LibroService.actualizarPortada ANTES de guardar). Clave nueva de
-- configuracion_sistema, mismo patron de V4__configuracion_sistema_
-- valores_iniciales.sql (ON CONFLICT DO NOTHING: idempotente).
INSERT INTO configuracion_sistema (clave, valor) VALUES
    ('max_tamano_portada_mb', '2')
ON CONFLICT (clave) DO NOTHING;