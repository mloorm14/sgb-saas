## HU-AUTH-06: Auditar los eventos de autenticación

**Como** administrador/auditor del sistema,
**quiero** que cada inicio de sesión (exitoso o fallido) y cada cierre
de sesión queden registrados con la IP, la fecha/hora y el usuario o
correo involucrado,
**para** poder investigar un incidente de seguridad o un acceso
indebido después de que ya ocurrió, sin depender de que alguien lo
haya reportado en el momento.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Registro de eventos de autenticación (OWASP A09)

  Escenario: Un login exitoso queda registrado con IP, timestamp y usuario
    Dado que el usuario "lector@correo.com" inicia sesión correctamente desde la IP "172.18.0.1"
    Entonces se registra un evento LOGIN_OK con esa IP, la fecha/hora, y el id del usuario
    Y ese evento es consultable tanto en los logs de la aplicación como en la tabla bitacora_auditoria

  Escenario: Un login fallido queda registrado
    Dado que alguien intenta iniciar sesión con el correo "lector@correo.com" y una contraseña incorrecta
    Entonces se registra un evento LOGIN_FAIL con el correo intentado, la IP, y la fecha/hora
    (no hay un id de usuario que asociar todavía, porque la autenticación nunca llegó a resolverlo)

  Escenario: Un logout queda registrado
    Dado que el usuario cierra sesión
    Entonces se registra un evento LOGOUT con su correo, la IP, y la fecha/hora
```
