## CU-F02: Consultar mis préstamos desde la interfaz (lector)

- **Actor principal**: Lector (usando la interfaz web).
- **Interesados y sus intereses**:
  - Lector: quiere saber rápido qué libros tiene prestados y hasta
    cuándo, sin pasos adicionales ni tener que ingresar su propio ID.
- **Precondiciones**: el lector tiene sesión iniciada.
- **Garantía de éxito**: el lector ve exactamente sus propios préstamos
  (nunca los de otro usuario), paginados, con estado claro de cada uno.
- **Disparador**: el lector navega a la sección "Mis préstamos".

### Escenario principal (flujo básico)

1. El lector hace clic en "Mis préstamos" en el menú.
2. La interfaz obtiene el id del usuario logueado a partir del JWT en
   memoria, sin pedírselo al lector.
3. La interfaz llama a `GET /api/v1/prestamos/usuario/{miId}` paginado.
4. La tabla se llena con los préstamos del lector: libro, fecha de
   préstamo, fecha límite y fecha de devolución real si aplica.
5. El lector puede navegar entre páginas con "Anterior"/"Siguiente".

### Extensiones (flujos alternativos)

- **3a.** El backend responde con error: la interfaz muestra `errorMsg`
  ("Error al cargar tus préstamos") y no deja la pantalla en un estado
  de carga infinita.
- **4a.** El lector no tiene ningún préstamo: la tabla muestra el
  mensaje "No tenés préstamos registrados" en vez de una tabla vacía.