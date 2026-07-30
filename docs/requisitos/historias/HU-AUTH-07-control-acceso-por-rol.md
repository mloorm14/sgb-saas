## HU-AUTH-07: Que cada acción respete el rol real del usuario

**Como** gerente del sistema,
**quiero** que cada endpoint verifique el rol del usuario únicamente
desde su sesión autenticada,
**para** que ningún usuario pueda ejecutar una acción reservada a otro
rol, ni siquiera manipulando manualmente los datos que envía en el
request.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Control de acceso basado en roles (RBAC)

  Escenario: Un endpoint restringido rechaza a un rol no autorizado
    Dado que un usuario con rol BIBLIOTECARIO tiene sesión iniciada
    Cuando intenta anular una multa (acción reservada a GERENTE/ADMIN)
    Entonces el sistema rechaza la operación con un error 403
    Y la operación se rechaza antes de ejecutar cualquier lógica de negocio

  Escenario: El rol nunca se toma del cuerpo del request
    Dado que un usuario con rol BIBLIOTECARIO envía "rolEjecutor": "GERENTE" en el body
    Cuando intenta anular una multa
    Entonces el sistema ignora ese campo del body por completo
    Y resuelve el rol real únicamente desde la sesión autenticada (el token JWT)
    Y la operación se rechaza igual, porque el rol real sigue siendo BIBLIOTECARIO

  Escenario: Defensa en profundidad a nivel de base de datos
    Dado que, por algún motivo, la verificación de rol en el controller se saltara
    Cuando el procedimiento almacenado sp_anular_multa recibe un rol distinto a GERENTE/ADMIN
    Entonces el procedimiento igual rechaza la operación (SQLSTATE LB422)
    Y no depende únicamente de la capa de aplicación para protegerse
```
