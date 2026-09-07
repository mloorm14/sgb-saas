## HU-AUD-01: Consultar la bitácora de auditoría del sistema

**Como** gerente o administrador,
**quiero** poder consultar de forma paginada y filtrable los eventos
registrados en la bitácora de auditoría, ver un resumen agregado y
exportarlos como CSV,
**para** poder investigar qué pasó en el sistema sin depender de una
consulta manual a la base de datos.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Consulta de la bitácora de auditoría

  Escenario: GERENTE o ADMIN consultan el listado filtrado
    Dado que un usuario con rol GERENTE o ADMIN tiene sesión iniciada
    Cuando consulta la bitácora filtrando por usuario, módulo o rango de fechas
    Entonces el sistema responde 200 con la página de eventos que cumple los filtros

  Escenario: GERENTE o ADMIN consultan el resumen agregado
    Dado que un usuario con rol GERENTE o ADMIN tiene sesión iniciada
    Cuando pide el resumen de auditoría
    Entonces el sistema responde 200 con el total de eventos por tabla afectada

  Escenario: GERENTE o ADMIN exportan la bitácora como CSV
    Dado que un usuario con rol GERENTE o ADMIN tiene sesión iniciada
    Cuando pide exportar la bitácora con los mismos filtros del listado
    Entonces el sistema responde con un archivo CSV descargable

  Escenario: Un rol distinto de GERENTE/ADMIN no puede acceder a la auditoría
    Dado que un usuario con rol BIBLIOTECARIO o LECTOR tiene sesión iniciada
    Cuando intenta consultar, resumir o exportar la bitácora de auditoría
    Entonces el sistema rechaza la operación con un error 403
    Y la operación se rechaza antes de ejecutar cualquier consulta
```
