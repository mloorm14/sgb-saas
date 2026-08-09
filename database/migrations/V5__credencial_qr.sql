-- Credencial QR de usuario (Modulo 8): un UUID por usuario, generado una
-- sola vez, que se codifica dentro del QR. Nunca contiene el correo ni la
-- identificacion en claro -- es solo el mecanismo de verificacion de
-- identidad que el bibliotecario escanea en ventanilla.
-- uuid_generate_v4() ya esta disponible: la extension "uuid-ossp" se creo
-- en el schema base (ver db/schema.sql) para el token de refresh_tokens.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS credencial_qr_token UUID NOT NULL DEFAULT uuid_generate_v4();

CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_credencial_qr_token
    ON usuarios (credencial_qr_token);
