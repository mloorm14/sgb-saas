## HU-F02: Ver mis préstamos activos e históricos desde la interfaz

**Como** lector,
**quiero** ver la lista de mis préstamos, tanto los que tengo activos
como los que ya devolví,
**para** saber qué libros tengo pendientes de devolver y para cuándo,
sin tener que preguntar en el mostrador.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Visualización de préstamos propios (vista lector)

  Escenario: El lector ve un préstamo activo con su fecha límite
    Dado que el lector "juan@uteq.edu.ec" tiene un préstamo sin devolver
    Cuando el lector abre la sección "Mis préstamos"
    Entonces ve el libro, la fecha en que lo retiró, y la fecha límite de devolución

  Escenario: El lector distingue un préstamo ya devuelto de uno activo
    Dado que el lector tiene un préstamo con fecha de devolución real registrada
    Cuando el lector abre la sección "Mis préstamos"
    Entonces esa fila muestra la fecha real de devolución en vez de un guion vacío

  Escenario: El lector no tiene préstamos registrados
    Dado que el lector nunca ha pedido un libro prestado
    Cuando el lector abre la sección "Mis préstamos"
    Entonces ve un mensaje indicando que no tiene préstamos registrados, en vez de una tabla vacía sin explicación

  Escenario: Un error del servidor no rompe la pantalla
    Dado que el backend de préstamos no responde
    Cuando el lector abre la sección "Mis préstamos"
    Entonces ve un mensaje de error claro y la pantalla sigue siendo usable (puede navegar a otra sección)
```