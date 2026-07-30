## HU-LIB-02: Gestionar el catálogo de libros

**Como** bibliotecario o gerente,
**quiero** crear, editar y dar de baja libros del catálogo,
**para** mantenerlo actualizado con los ejemplares reales que tiene la
biblioteca.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Gestión del catálogo de libros

  Escenario: Crear un libro con ISBN nuevo
    Dado que ningún libro tiene el ISBN "9780132350884"
    Cuando el bibliotecario crea un libro con ese ISBN y datos válidos
    Entonces el sistema responde 201 con el libro creado

  Escenario: Intento de crear un libro con ISBN duplicado
    Dado que ya existe un libro con el ISBN "9780132350884"
    Cuando el bibliotecario intenta crear otro libro con el mismo ISBN
    Entonces el sistema rechaza la operación con un error 400

  Escenario: Editar un libro existente
    Dado que el libro con id 3 existe
    Cuando el bibliotecario actualiza su título y stock
    Entonces el sistema responde 200 con los datos actualizados

  Escenario: Dar de baja un libro (baja lógica, no borrado físico)
    Dado que el libro con id 3 existe y está ACTIVO
    Cuando el gerente lo elimina desde el catálogo
    Entonces el sistema responde 204
    Y el libro pasa a estado DADO_DE_BAJA (nunca se borra la fila de la base de datos)
    Y deja de aparecer en el listado y en la consulta por id

  Escenario: Un lector intenta gestionar el catálogo
    Dado que el usuario tiene rol LECTOR
    Cuando intenta crear, editar o eliminar un libro
    Entonces el sistema rechaza la operación con un error 403
```
