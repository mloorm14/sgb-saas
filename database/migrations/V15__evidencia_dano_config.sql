-- V15: Agregar clave de configuración para límite de tamaño de evidencia fotográfica.
-- Reutiliza el mismo patrón de max_tamano_portada_mb (V13).
-- El Admin puede editar este valor desde /admin/configuracion.

INSERT INTO configuracion_sistema (clave, valor)
VALUES ('max_tamano_evidencia_mb', '2')
ON CONFLICT (clave) DO NOTHING;
