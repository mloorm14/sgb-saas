## HU-ADM-01: Administrar rol y estado de las cuentas de usuario

**Como** administrador del sistema,
**quiero** poder listar el padrón de usuarios y cambiar el rol o el
estado de una cuenta,
**para** poder gestionar permisos y bloquear/reactivar cuentas sin
intervenir directamente en la base de datos.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Administración de usuarios

  Escenario: ADMIN o GERENTE listan el padrón de usuarios
    Dado que un usuario con rol ADMIN o GERENTE tiene sesión iniciada
    Cuando consulta el listado paginado de usuarios
    Entonces el sistema responde 200 con el padrón

  Escenario: ADMIN cambia rol o estado de cualquier cuenta
    Dado que un usuario con rol ADMIN tiene sesión iniciada
    Cuando cambia el rol o el estado de cualquier cuenta, a cualquier valor válido
    Entonces el sistema responde 204 y el cambio queda aplicado

  Escenario: GERENTE cambia rol o estado, pero solo dentro de su alcance permitido
    Dado que un usuario con rol GERENTE tiene sesión iniciada
    Y la cuenta objetivo fue creada por ese mismo GERENTE
    Cuando le asigna el rol LECTOR o BIBLIOTECARIO, o lo activa/inactiva
    Entonces el sistema responde 204 y el cambio queda aplicado

  Escenario: GERENTE fuera de su alcance permitido
    Dado que un usuario con rol GERENTE tiene sesión iniciada
    Cuando intenta asignar un rol distinto de LECTOR/BIBLIOTECARIO,
      o un estado distinto de ACTIVO/INACTIVO,
      o modificar una cuenta que él mismo no creó
    Entonces el sistema rechaza la operación

  Escenario: Solo ADMIN puede dar de baja lógica una cuenta
    Dado que un usuario con rol GERENTE tiene sesión iniciada
    Cuando intenta dar de baja (soft-delete) una cuenta de usuario
    Entonces el sistema rechaza la operación con un error 403
```
