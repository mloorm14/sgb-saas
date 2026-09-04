## HU-F01: Ver y pagar una multa desde la interfaz

**Como** lector con una multa pendiente,
**quiero** ver el detalle de mi multa y poder pagarla desde la
aplicación web,
**para** regularizar mi situación y volver a poder pedir libros
prestados sin tener que llamar o ir presencialmente a preguntar.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Visualización y pago de multas (vista lector)

  Escenario: El lector ve su multa pendiente con el monto correcto
    Dado que el lector "juan@correo.com" tiene una multa con estado PENDIENTE
    Cuando el lector abre la sección "Mis multas"
    Entonces ve el monto, la fecha en que se generó, y el estado "Pendiente"

  Escenario: El lector no puede pagar su propia multa desde la UI
    Dado que el lector "juan@correo.com" está en la sección "Mis multas"
    Entonces no ve ningún botón de "Pagar" ni "Anular"
    Y ve un mensaje indicando que debe acercarse a la biblioteca para regularizarla

  Escenario: El bibliotecario paga una multa desde su vista de gestión
    Dado que el bibliotecario está en la sección "Gestión de multas"
    Y selecciona la multa pendiente de "juan@correo.com"
    Cuando hace clic en "Pagar" y confirma
    Entonces la multa cambia a estado "Pagada" en la lista
    Y si era la única multa pendiente del lector, dejaría de estar bloqueado (verificable en un siguiente intento de préstamo)
```