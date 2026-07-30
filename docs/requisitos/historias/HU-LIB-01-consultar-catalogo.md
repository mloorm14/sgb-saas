## HU-LIB-01: Consultar el catálogo de libros

**Como** usuario con sesión iniciada (LECTOR, BIBLIOTECARIO o
GERENTE),
**quiero** ver el listado paginado de libros y el detalle de uno en
particular,
**para** saber qué libros existen, su stock disponible, y decidir si
pedirlo prestado o reservarlo.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Consulta del catálogo de libros

  Escenario: Listado paginado exitoso
    Dado que el usuario tiene sesión iniciada con rol LECTOR, BIBLIOTECARIO o GERENTE
    Cuando solicita el listado de libros
    Entonces el sistema responde 200 con una página de libros ordenada por título
    Y cada libro incluye su stock disponible actual

  Escenario: Ver el detalle de un libro existente
    Dado que el libro con id 3 existe y está en estado ACTIVO
    Cuando el usuario consulta ese libro por su id
    Entonces el sistema responde 200 con los datos completos del libro

  Escenario: Intento de ver un libro dado de baja o inexistente
    Dado que el libro con id 99 no existe, o fue dado de baja
    Cuando el usuario intenta consultarlo por su id
    Entonces el sistema responde 404
```
