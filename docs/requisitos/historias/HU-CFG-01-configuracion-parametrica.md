## HU-CFG-01: Ajustar parámetros del sistema sin un despliegue nuevo

**Como** administrador del sistema,
**quiero** poder listar y editar los parámetros clave-valor del sistema
(ej. el máximo de renovaciones de un préstamo o los días de préstamo por
defecto),
**para** poder ajustar reglas de negocio operativas sin depender de que el
equipo de desarrollo haga un release nuevo solo para cambiar un número.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Configuración paramétrica del sistema

  Escenario: ADMIN lista los parámetros actuales
    Dado que un usuario con rol ADMIN tiene sesión iniciada
    Cuando consulta el listado de configuración del sistema
    Entonces el sistema responde 200 con todas las claves y valores actuales

  Escenario: ADMIN actualiza una clave existente
    Dado que un usuario con rol ADMIN tiene sesión iniciada
    Y la clave "max_renovaciones_default" existe en la configuración
    Cuando actualiza esa clave con un nuevo valor
    Entonces el sistema responde 200 con el valor nuevo
    Y ese valor queda disponible de inmediato para el resto del sistema

  Escenario: Un rol distinto de ADMIN no puede leer ni editar la configuración
    Dado que un usuario con rol GERENTE, BIBLIOTECARIO o LECTOR tiene sesión iniciada
    Cuando intenta listar o actualizar un parámetro del sistema
    Entonces el sistema rechaza la operación con un error 403
```
