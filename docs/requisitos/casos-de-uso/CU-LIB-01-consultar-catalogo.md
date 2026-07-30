## CU-LIB-01: Consultar el catálogo de libros

- **Actor principal**: Usuario autenticado con rol LECTOR,
  BIBLIOTECARIO o GERENTE.
- **Interesados y sus intereses**:
  - Lector: quiere saber qué libros hay disponibles antes de ir a
    pedir un préstamo o hacer una reservación.
  - Bibliotecario/Gerente: quieren consultar el catálogo mientras
    atienden a un lector o registran un préstamo.
- **Precondiciones**: sesión iniciada con uno de los roles permitidos.
- **Garantía de éxito (postcondición)**: se devuelve una página de
  libros en estado `ACTIVO` (los `DADO_DE_BAJA` no aparecen), con
  `page`/`size` como query params y ordenados por `titulo` por
  defecto; el resultado se sirve desde el cache Redis `"libros"` si
  está caliente (TTL configurable, ver `adr-008-ttl-cache-libros.md`).
- **Disparador**: `GET /api/v1/libros` (listado) o
  `GET /api/v1/libros/{id}` (detalle).

### Escenario principal (flujo básico)

1. El usuario solicita el listado (con `page`/`size` opcionales) o el
   detalle de un libro por id.
2. El sistema resuelve el rol del usuario y verifica que esté
   permitido (`@PreAuthorize`).
3. El sistema consulta los libros en estado `ACTIVO` (cache Redis si
   está disponible, base de datos si no).
4. El sistema responde con la página de resultados o el detalle del
   libro.

### Extensiones (flujos alternativos)

- **1a.** El libro consultado por id no existe, o existe pero está
  `DADO_DE_BAJA`: el sistema responde `404`
  (`EntityNotFoundException`) — un libro dado de baja se trata igual
  que uno inexistente para efectos de consulta.
- **2a.** El usuario tiene un rol no incluido en la lista permitida de
  este endpoint (ver el hallazgo sobre `ADMIN` documentado en
  `CU-AUTH-07-control-acceso-por-rol.md`): el sistema responde `403`.
