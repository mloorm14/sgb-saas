## CU-LIB-02: Gestionar el catálogo de libros (crear, editar, dar de baja)

- **Actor principal**: Bibliotecario o Gerente.
- **Interesados y sus intereses**:
  - Bibliotecario/Gerente: quieren mantener el catálogo fiel a los
    ejemplares reales, sin duplicados por ISBN.
  - Lector: quiere que el stock mostrado sea confiable al momento de
    decidir pedir un préstamo o hacer una reservación.
- **Precondiciones**: sesión iniciada con rol BIBLIOTECARIO o GERENTE.
- **Garantía de éxito (postcondición)**:
  - Crear: existe una fila nueva en `libros` con ISBN único.
  - Editar: la fila existente se actualiza; el ISBN sigue siendo único
    entre todos los libros (excepto el propio).
  - Eliminar: el libro pasa a `estado = DADO_DE_BAJA` — **baja
    lógica**, la fila nunca se borra físicamente de la base de datos.
  - En los tres casos, el cache Redis `"libros"` se invalida
    (`@CacheEvict`) para que el próximo listado refleje el cambio.
- **Disparador**: `POST /api/v1/libros` (crear),
  `PUT /api/v1/libros/{id}` (editar),
  `DELETE /api/v1/libros/{id}` (dar de baja).

### Escenario principal (flujo básico — crear)

1. El bibliotecario/gerente completa los datos del libro (título,
   ISBN, editorial, idioma, stock total/disponible, etc.).
2. El sistema valida el rol (`@PreAuthorize`) y los datos del DTO
   (`@Valid`).
3. El sistema verifica que el ISBN no esté ya registrado.
4. El sistema valida que `stockDisponible` no sea mayor a `stockTotal`.
5. El sistema crea el libro, invalida el cache y responde `201` con
   el libro creado.

### Extensiones (flujos alternativos)

- **3a.** El ISBN ya está registrado (en creación) o pertenece a otro
  libro (en edición): el sistema rechaza con error `400`
  (`IllegalArgumentException` — nótese que este caso responde `400`,
  no `409`, a diferencia de otros conflictos del sistema como
  préstamos duplicados).
- **4a.** `stockDisponible > stockTotal`: el sistema rechaza con error
  `400`.
- **Editar/Eliminar sobre un id inexistente**: el sistema responde
  `404` (`EntityNotFoundException`).
- **Rol no autorizado** (ej. LECTOR): el sistema responde `403` antes
  de ejecutar cualquier lógica de negocio (ver
  `CU-AUTH-07-control-acceso-por-rol.md`).
