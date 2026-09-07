## HU-PRE-03: Renovar un préstamo sin devolverlo y volver a pedirlo

**Como** lector con un préstamo activo,
**quiero** poder renovar mi préstamo para extender la fecha límite de
devolución,
**para** no tener que devolver físicamente el libro y volver a pedirlo
cuando todavía lo necesito.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Renovación de préstamo

  Escenario: Renovación exitosa dentro de los límites permitidos
    Dado que un préstamo está ACTIVO, no vencido, y bajo el máximo de renovaciones
    Y no existe una reserva vigente de otro usuario sobre el mismo libro
    Cuando el lector dueño del préstamo (o BIBLIOTECARIO/GERENTE/ADMIN) lo renueva
    Entonces la fecha límite de devolución se extiende
    Y el contador de renovaciones del préstamo aumenta en 1

  Escenario: Rechazo por préstamo vencido
    Dado que la fecha límite de devolución de un préstamo ya pasó
    Cuando alguien intenta renovarlo
    Entonces el sistema rechaza la renovación

  Escenario: Rechazo por límite de renovaciones alcanzado
    Dado que un préstamo ya alcanzó el máximo de renovaciones configurado
    Cuando alguien intenta renovarlo de nuevo
    Entonces el sistema rechaza la renovación

  Escenario: Rechazo por reserva vigente de otro usuario
    Dado que otro usuario tiene una reserva vigente sobre el mismo libro
    Cuando el lector con el préstamo intenta renovarlo
    Entonces el sistema rechaza la renovación, porque el libro debe quedar libre para esa reserva

  Escenario: Un lector no puede renovar el préstamo de otro
    Dado que un LECTOR intenta renovar un préstamo que no es suyo
    Cuando envía la solicitud de renovación
    Entonces el sistema rechaza el acceso
```
