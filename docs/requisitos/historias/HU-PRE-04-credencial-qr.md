## HU-PRE-04: Identificarme en mostrador con mi código QR

**Como** lector,
**quiero** poder obtener la imagen de mi propio código QR y que el
bibliotecario pueda usarlo para identificarme al registrar un préstamo,
**para** no tener que dar mi usuario o buscarme por nombre cada vez que
pido un libro en mostrador.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Credencial QR

  Escenario: Un lector obtiene su propio código QR
    Dado que un usuario con rol LECTOR tiene sesión iniciada
    Cuando pide su credencial QR
    Entonces el sistema responde 200 con una imagen PNG del código

  Escenario: Un rol distinto de LECTOR no puede pedir una credencial QR
    Dado que un usuario con rol BIBLIOTECARIO, GERENTE o ADMIN tiene sesión iniciada
    Cuando intenta pedir una credencial QR
    Entonces el sistema rechaza la operación con un error 403

  Escenario: Registrar un préstamo identificando al lector por su token QR
    Dado que un lector tiene un token de credencial QR válido
    Cuando el bibliotecario registra un préstamo usando ese token en vez del usuarioId
    Entonces el sistema resuelve al usuario dueño del token
    Y el préstamo se registra a nombre de ese usuario
```
