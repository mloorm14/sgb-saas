-- 3 nuevos parámetros de configuración para el admin:
-- 1. correo_dominios_permitidos: restricción de registro por dominio de correo
-- 2. max_prestamos_usuario: máximo de préstamos simultáneos por lector
-- 3. dias_anticipacion_vencimiento: días antes del vencimiento para enviar recordatorio
INSERT INTO configuracion_sistema (clave, valor) VALUES
('correo_dominios_permitidos', 'uteq.edu.ec'),
('max_prestamos_usuario', '3'),
('dias_anticipacion_vencimiento', '3');
