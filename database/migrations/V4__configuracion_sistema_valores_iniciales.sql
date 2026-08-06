-- Valores iniciales de configuracion_sistema que aun faltan (Modulo 9.4).
-- monto_multa_diaria ya existe via db/seed.sql (la usa
-- sp_registrar_devolucion.sql). Estas tres son nuevas: hoy estan
-- hardcodeadas en el codigo (ej. ReservacionService.DIAS_LIMITE_RETIRO) y
-- se centralizan aqui para que el Admin las pueda ajustar sin un
-- despliegue nuevo (ver ConfiguracionSistemaService). ON CONFLICT DO
-- NOTHING: idempotente si alguna clave ya fue insertada a mano en algun
-- ambiente.
INSERT INTO configuracion_sistema (clave, valor) VALUES
    ('minutos_reserva', '1440'),
    ('dias_prestamo_default', '15'),
    ('max_renovaciones_default', '2')
ON CONFLICT (clave) DO NOTHING;
