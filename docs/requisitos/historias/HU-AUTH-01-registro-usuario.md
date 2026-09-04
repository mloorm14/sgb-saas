## HU-AUTH-01: Registrarme como nuevo usuario

**Como** visitante sin cuenta en el sistema,
**quiero** crear una cuenta con mi correo institucional y una contraseña,
**para** poder iniciar sesión y acceder al catálogo de libros y a mis
préstamos/reservaciones/multas según mi rol.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Registro de usuario

  Escenario: Registro exitoso con datos válidos
    Dado que no existe ningún usuario con el correo "nuevo@correo.com"
    Cuando el visitante se registra con nombre, apellido, correo "nuevo@correo.com" y una contraseña de 8 o más caracteres
    Entonces el sistema responde 201 con los datos del usuario creado
    Y el usuario queda con rol "LECTOR" y estado "ACTIVO" por defecto
    Y la contraseña se almacena hasheada, nunca en texto plano

  Escenario: Intento de registro con correo ya registrado
    Dado que ya existe un usuario con el correo "lector@correo.com"
    Cuando alguien intenta registrarse de nuevo con ese mismo correo
    Entonces el sistema rechaza la operación con un error 409
    Y no se crea ningún usuario nuevo

  Escenario: Intento de registro con contraseña demasiado corta
    Dado que el visitante completa el formulario con una contraseña de menos de 8 caracteres
    Cuando envía el formulario
    Entonces el sistema rechaza la operación con un error 400
    Y el mensaje indica que la contraseña debe tener al menos 8 caracteres
```
