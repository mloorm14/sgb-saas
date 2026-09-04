## HU-F03: Reservar un libro desde la interfaz

**Como** lector,
**quiero** reservar un libro para mí mismo desde la aplicación web,
**para** asegurarme un ejemplar sin tener que llamar o ir
presencialmente a preguntar si hay disponibilidad.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Creación de reservaciones (vista lector)

  Escenario: El lector reserva un libro para sí mismo sin elegir usuario
    Dado que el lector "juan@correo.com" está en la sección "Mis reservaciones"
    Cuando completa el ID del libro y confirma la reservación
    Entonces la reservación se crea a su propio nombre, sin que la interfaz le pida elegir un usuario

  Escenario: La reservación creada aparece de inmediato en el listado propio
    Dado que el lector acaba de reservar un libro
    Cuando la reservación se confirma con éxito
    Entonces la nueva reservación aparece en su listado de "Mis reservaciones" sin recargar la página completa

  Escenario: El backend rechaza la reservación
    Dado que el libro solicitado no tiene ejemplares disponibles
    Cuando el lector intenta reservarlo
    Entonces la interfaz muestra un mensaje de error claro y el formulario permanece completo para reintentar

  Escenario: El bibliotecario reserva un libro a nombre de otro usuario
    Dado que el bibliotecario está en la sección "Gestión de reservaciones"
    Cuando completa el ID del usuario y del libro, y confirma
    Entonces la reservación se crea a nombre del usuario indicado, no del bibliotecario
```