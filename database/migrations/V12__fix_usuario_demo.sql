-- ============================================================================
-- SGB-SaaS — V12__fix_usuario_demo.sql
-- Migración VERSIONADA (se aplica una sola vez por base).
--
-- POR QUÉ EXISTE: la V11__seed_usuario_demo.sql inserta u@uteq.edu.ec con
-- ON CONFLICT (correo) DO NOTHING. Si la cuenta ya existía en la base
-- ANTES de V11 (creada desde el formulario de registro público, que crea
-- el usuario con estado PENDIENTE_VERIFICACION — ver AuthService), el
-- INSERT de V11 no hacía nada y la cuenta quedaba deshabilitada: el login
-- devolvía 403 (DisabledException, UserDetailsServiceImpl:
-- ESTADOS_DESHABILITADOS = INACTIVO, PENDIENTE_VERIFICACION).
--
-- Esta migración NORMALIZA la cuenta demo de forma idempotente y
-- determinista, funcione o no haya funcionado V11:
--   1) la crea si no existe (mismo INSERT idempotente de V11);
--   2) fuerza estado ACTIVO, correo_verificado = TRUE y el hash de la
--      contraseña pública "usuario1" (cualquier estado previo de la
--      cuenta queda corregido);
--   3) garantiza el rol LECTOR (PK compuesta -> ON CONFLICT DO NOTHING).
-- Es un UPDATE "a ciegas" deliberado sobre esa única fila (correo es
-- UNIQUE vía idx_usuarios_correo): la cuenta demo es un contrato público
-- documentado en el README, no puede quedar a merced de su historial.
-- ============================================================================

INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado)
VALUES (
    'Usuario',
    'Demo',
    'u@uteq.edu.ec',
    '$2a$12$h1.Cc0mZA1T/L13tFiq61OA7rrdLjDMZGiO36IDqZoRgjIoxSMeFe',
    (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
    TRUE
)
ON CONFLICT (correo) DO NOTHING;

UPDATE usuarios
   SET password_hash      = '$2a$12$h1.Cc0mZA1T/L13tFiq61OA7rrdLjDMZGiO36IDqZoRgjIoxSMeFe',
       estado_id          = (SELECT id FROM estados_usuario WHERE nombre = 'ACTIVO'),
       correo_verificado  = TRUE
 WHERE correo = 'u@uteq.edu.ec';

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id
  FROM usuarios u, roles r
 WHERE u.correo = 'u@uteq.edu.ec'
   AND r.nombre = 'LECTOR'
ON CONFLICT DO NOTHING;
