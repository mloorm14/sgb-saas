-- ============================================================================
-- SGB-SaaS — V11__seed_usuario_demo.sql
-- Migración VERSIONADA (se aplica una sola vez por base).
--
-- PROPÓSITO: crear un usuario demo SEPARADO del admin real, con rol
-- limitado (LECTOR — sin permisos administrativos), para que el tribunal
-- pueda entrar al sistema desplegado sin registrarse (guía de la Entrega
-- Final: credenciales publicadas en el README).
--
-- DECISIÓN (opción a, confirmada por el equipo): el usuario demo es una
-- entidad distinta de admin@sgb-saas.local — no reutiliza el admin ni
-- modifica su credencial. Su rol es únicamente LECTOR, que en el RBAC
-- normalizado (V2) es el rol sin permisos administrativos.
--
-- Idempotencia: mismo criterio que V10__seed_catalogos_y_admin.sql —
-- correo es UNIQUE vía idx_usuarios_correo (no inline) -> ON CONFLICT
-- (correo); usuario_roles tiene PK compuesta -> ON CONFLICT DO NOTHING.
-- En la base sembrada local (make up) también corre después del baseline
-- y debe tolerar filas existentes, igual que V10.
--
-- Password: hash BCrypt (costo 12, $2a$ — mismo formato que el admin de
-- V10) de la contraseña de desarrollo "usuario1", generado con
-- openssl/bcrypt y verificado con bcrypt.checkpw. La contraseña real se
-- documenta SOLO en el README (sección "Cuenta demo") — nunca en otra
-- parte del repositorio.
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

INSERT INTO usuario_roles (usuario_id, rol_id)
SELECT u.id, r.id
  FROM usuarios u, roles r
 WHERE u.correo = 'u@uteq.edu.ec'
   AND r.nombre = 'LECTOR'
ON CONFLICT DO NOTHING;
