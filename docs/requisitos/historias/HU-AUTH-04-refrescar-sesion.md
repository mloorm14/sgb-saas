## HU-AUTH-04: Refrescar mi sesión sin volver a escribir la contraseña

**Como** usuario con sesión iniciada cuyo `accessToken` acaba de
expirar,
**quiero** obtener un `accessToken` nuevo automáticamente,
**para** seguir usando el sistema sin que se me cierre la sesión ni
tenga que volver a escribir mi contraseña cada hora.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Refresco de accessToken vía cookie refreshToken

  Escenario: Refresh exitoso con la cookie refreshToken presente y válida
    Dado que el navegador tiene la cookie refreshToken (HttpOnly) vigente
    Cuando el frontend recibe un 401 fuera de /auth/ e intenta refrescar
    Entonces el sistema responde 200 con un accessToken nuevo
    Y el frontend reintenta automáticamente la request original con el token nuevo
    Y el usuario no nota ninguna interrupción de sesión

  Escenario: Intento de refresh sin la cookie
    Dado que la request no trae la cookie refreshToken
    Cuando se llama a POST /api/auth/refresh
    Entonces el sistema rechaza la operación con un error 400

  Escenario: Refresh con refreshToken inválido o expirado
    Dado que la cookie refreshToken existe pero el token ya no es válido
    Cuando se intenta refrescar la sesión
    Entonces el sistema no emite ningún accessToken nuevo
    Y el frontend cierra la sesión y redirige a login (comportamiento del interceptor, no del backend)
```
