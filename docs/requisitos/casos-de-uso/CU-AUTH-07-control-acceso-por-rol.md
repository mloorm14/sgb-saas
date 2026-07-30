## CU-AUTH-07: Verificar el rol real del usuario antes de ejecutar una acción restringida

- **Actor principal**: El sistema (verificación automática en cada
  request a un endpoint restringido).
- **Interesados y sus intereses**:
  - Gerente/Administrador: quiere que las acciones sensibles (anular
    multa, gestionar catálogo, registrar préstamos) queden reservadas
    a los roles correctos, sin excepciones.
  - Cualquier usuario: quiere que su rol real (no uno que él mismo
    declare) sea el único que determine lo que puede hacer.
- **Precondiciones**: el usuario tiene una sesión válida (JWT no
  expirado, no revocado); el modelo RBAC está normalizado en
  `roles` + `usuario_roles` + `permisos` (`adr-010-autenticacion-jwt-rbac.md`).
- **Garantía de éxito (postcondición)**: toda acción restringida se
  ejecuta si y solo si el rol resuelto desde `Authentication`
  (`SecurityContext`, poblado a partir del JWT validado) está en la
  lista de roles permitidos para ese endpoint — nunca desde un campo
  del body del request.
- **Disparador**: cualquier request a un endpoint anotado con
  `@PreAuthorize`.

### Escenario principal (flujo básico)

1. El usuario autenticado hace una request a un endpoint restringido
   (ej. `POST /api/v1/multas/{id}/anulacion`).
2. Spring Security resuelve las autoridades (`ROLE_X`) del usuario
   desde el `SecurityContext`, poblado por el filtro JWT a partir del
   token validado — nunca desde el body de la request.
3. `@PreAuthorize("hasAnyRole(...)")` evalúa si el rol real del usuario
   está en la lista permitida para ese endpoint, **antes** de que la
   request llegue al método del controller.
4. Si el rol está permitido, la request continúa al service. Algunos
   flujos (ej. `sp_anular_multa`) revalidan el rol una segunda vez a
   nivel de procedimiento almacenado, como defensa en profundidad.
5. La acción se ejecuta y responde normalmente.

### Extensiones (flujos alternativos)

- **3a.** El rol del usuario no está en la lista permitida: Spring
  Security lanza `AuthorizationDeniedException`, que
  `GlobalExceptionHandler` traduce a `403` — la request nunca llega a
  ejecutar lógica de negocio.
- **1a.** El request incluye un campo con un rol distinto en el body
  (ej. `"rolEjecutor": "GERENTE"` en el DTO de anulación de multa): el
  DTO de request no expone ese campo — se ignora por completo, no
  existe ningún camino de código que lo lea. El rol real sigue siendo
  el resuelto en el paso 2.
- **Hallazgo real verificado durante la auditoría de `LibroController`**:
  hoy `GET /api/v1/libros` y `GET /api/v1/libros/{id}` permiten
  `LECTOR`, `BIBLIOTECARIO` y `GERENTE`, pero **no incluyen `ADMIN`** en
  la lista — un usuario con rol exclusivamente `ADMIN` recibe `403` al
  intentar consultar el catálogo. Documentado aquí como comportamiento
  real verificado en código (`LibroController.java`), no como un bug
  confirmado — puede ser intencional (ADMIN como rol de sistema, no de
  operación diaria de biblioteca) o un descuido; queda para que
  producto lo confirme, no se corrige como parte de este relevamiento.

**Evidencia empírica**: `docs/mediciones/sec/2026-07-30-owasp-a01-control-acceso-roto.md`.
