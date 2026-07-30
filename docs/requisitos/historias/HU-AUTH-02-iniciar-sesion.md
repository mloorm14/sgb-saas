## HU-AUTH-02: Iniciar sesión

**Como** usuario ya registrado,
**quiero** iniciar sesión con mi correo y contraseña,
**para** acceder a las funciones del sistema que correspondan a mi rol
(catálogo, préstamos, reservaciones, multas, gestión).

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Inicio de sesión

  Escenario: Login exitoso con credenciales correctas
    Dado que el usuario "lector@uteq.edu.ec" existe con estado ACTIVO
    Cuando inicia sesión con su correo y contraseña correctos
    Entonces el sistema responde 200 con un accessToken en el cuerpo
    Y el sistema adjunta el refreshToken en una cookie HttpOnly, Secure, SameSite=Strict
    Y el evento queda registrado como login exitoso (ver HU-AUTH-06)

  Escenario: Login con contraseña incorrecta
    Dado que el usuario "lector@uteq.edu.ec" existe
    Cuando intenta iniciar sesión con una contraseña incorrecta
    Entonces el sistema rechaza la operación con un error 401
    Y no se emite ningún token

  Escenario: Login de un usuario bloqueado por multas pendientes
    Dado que el usuario "maria@uteq.edu.ec" tiene estado BLOQUEADO_POR_MULTA
    Cuando intenta iniciar sesión con credenciales correctas
    Entonces el sistema rechaza la operación con un error 423
    Y el mensaje indica que debe regularizar sus multas pendientes

  Escenario: Login de un usuario inactivo o pendiente de verificación
    Dado que el usuario tiene estado INACTIVO o PENDIENTE_VERIFICACION
    Cuando intenta iniciar sesión con credenciales correctas
    Entonces el sistema rechaza la operación con un error 403
```
