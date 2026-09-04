-- V42: corrige doble rol demo u@uteq.edu.ec (LECTOR+GERENTE) dejado por V12
-- V12 solo agrega LECTOR ON CONFLICT DO NOTHING, nunca borra GERENTE huérfano.
-- Esta migración deja la cuenta demo con únicamente LECTOR, ACTIVO y verificado,
-- idempotente. Necesaria porque `make up` limpio reintroduce el bug aunque
-- Neon production ya esté parcheado a mano (README 2026-08-23).
DELETE FROM usuario_roles
WHERE usuario_id = (SELECT id FROM usuarios WHERE correo = 'u@uteq.edu.ec')
  AND rol_id != (SELECT id FROM roles WHERE nombre = 'LECTOR');

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id FROM usuarios u, roles r
WHERE u.correo = 'u@uteq.edu.ec' AND r.nombre = 'LECTOR'
ON CONFLICT DO NOTHING;

UPDATE usuarios
SET estado_id = (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    correo_verificado = true
WHERE correo = 'u@uteq.edu.ec'
  AND (estado_id != (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO') OR correo_verificado = false);
