## HU-AUTH-05: Bloqueo temporal tras varios intentos fallidos de login

**Como** responsable de seguridad del sistema,
**quiero** que una combinación correo+IP se bloquee temporalmente
después de varios intentos fallidos consecutivos de login,
**para** dificultar un ataque de fuerza bruta contra la contraseña de
un usuario, sin abrir una vía nueva para que un atacante bloquee a la
víctima usando el correo de otra persona.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Rate limiting de login (OWASP A07)

  Escenario: El sexto intento fallido consecutivo desde el mismo origen se bloquea
    Dado que el correo "victima@correo.com" desde la IP "203.0.113.10" ya falló 5 veces seguidas en los últimos 15 minutos
    Cuando se intenta un sexto login con ese mismo correo desde esa misma IP
    Entonces el sistema responde 429 sin siquiera intentar validar la contraseña
    Y el mensaje indica cuántos segundos faltan para poder reintentar

  Escenario: El bloqueo es por correo+IP, no solo por correo
    Dado que un atacante falla 5 intentos contra "victima@correo.com" desde su propia IP "198.51.100.5"
    Cuando la víctima real intenta iniciar sesión desde su propia IP "203.0.113.10"
    Entonces el intento de la víctima NO está bloqueado
    Y puede iniciar sesión normalmente si su contraseña es correcta

  Escenario: Un login exitoso resetea el contador de esa combinación
    Dado que el correo "lector@correo.com" desde una IP tiene 2 intentos fallidos registrados
    Cuando ese mismo correo desde esa misma IP inicia sesión correctamente
    Entonces el contador de intentos fallidos de esa combinación vuelve a cero
```
