-- V43: deja correo_dominios_permitidos neutro vacío (todos) para SaaS
-- V23 nació con 'uteq.edu.ec' ficticio; como es editable en UI Configuración
-- SaaS no debe nacer con sobrepuesta. Vacío = todos los dominios permitidos,
-- el tenant lo configura luego. Idempotente.
UPDATE configuracion_sistema
SET valor = ''
WHERE clave = 'correo_dominios_permitidos'
  AND valor = 'uteq.edu.ec';
