-- Configuracion para reservas: maximo por usuario y hora limite configurable por admin
INSERT INTO configuracion_sistema (clave, valor) VALUES
    ('max_reservas_por_usuario', '3'),
    ('hora_limite_retiro_reserva', '18:00')
ON CONFLICT (clave) DO NOTHING;
