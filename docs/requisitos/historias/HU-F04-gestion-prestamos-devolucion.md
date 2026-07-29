## HU-F04: Registrar un préstamo y su devolución desde la vista de gestión

**Como** bibliotecario,
**quiero** crear un préstamo para un usuario y registrar su devolución
desde la aplicación web,
**para** llevar el control de los libros prestados sin depender de
anotaciones manuales.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Gestión de préstamos y devoluciones (vista bibliotecario)

  Escenario: El bibliotecario crea un préstamo para un usuario
    Dado que el bibliotecario está en la sección "Gestión de préstamos"
    Cuando completa usuario, libro y días de préstamo, y confirma
    Entonces el préstamo queda registrado a nombre de ese usuario

  Escenario: El bibliotecario registra la devolución de un préstamo activo
    Dado que el bibliotecario está viendo los préstamos de un usuario
    Y uno de ellos no tiene fecha de devolución real
    Cuando hace clic en "Registrar devolución" para esa fila
    Entonces la fila se actualiza para mostrar la fecha de devolución real
    Y el botón "Registrar devolución" deja de aparecer en esa fila

  Escenario: El botón de devolución no aparece en préstamos ya devueltos
    Dado que un préstamo ya tiene fecha de devolución real registrada
    Cuando el bibliotecario ve ese préstamo en la tabla
    Entonces no ve ningún botón de "Registrar devolución" en esa fila

  Escenario: El backend rechaza la creación del préstamo
    Dado que el libro solicitado no tiene ejemplares disponibles
    Cuando el bibliotecario intenta crear el préstamo
    Entonces la interfaz muestra un mensaje de error claro sin cerrar el formulario
```