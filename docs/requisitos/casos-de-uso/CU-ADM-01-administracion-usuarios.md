## CU-ADM-01: Listar y administrar cuentas de usuario (rol/estado)

- **Actor principal**: ADMIN (sin restricción de alcance) y GERENTE (con
  restricción de alcance real, ver más abajo).
- **Interesados y sus intereses**:
  - Administrador: quiere control total sobre roles y estados de cuentas.
  - Gerente: quiere poder gestionar las cuentas que él mismo dio de alta
    (típicamente LECTOR/BIBLIOTECARIO), sin necesitar escalar cada cambio
    al administrador.
  - Cualquier usuario: quiere que su cuenta no pueda ser modificada por
    alguien sin autorización real para hacerlo.
- **Precondiciones**: el usuario ejecutor tiene sesión válida con rol
  `ADMIN` o `GERENTE`.
- **Garantía de éxito (postcondición)**: el rol o estado de la cuenta
  objetivo queda actualizado y auditado en `bitacora_auditoria`
  (`registrarAuditoria`, con el ejecutor resuelto desde el JWT, nunca
  desde el body).
- **Disparador**: `GET /api/v1/admin/usuarios`, `PATCH .../{id}/rol`,
  `PATCH .../{id}/estado`, `POST /api/v1/admin/usuarios`,
  `DELETE /api/v1/admin/usuarios/{id}`.

### Escenario principal (flujo básico)

1. El ejecutor (`ADMIN` o `GERENTE`) pide el listado paginado de usuarios.
2. `UsuarioAdminController` responde `200` (`@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")`
   en las cuatro rutas de lectura/escritura, `hasRole('ADMIN')` solo en
   `DELETE`).
3. El ejecutor pide cambiar el rol o el estado de una cuenta.
4. `UsuarioAdminService.cambiarRol`/`cambiarEstado` resuelve si el
   ejecutor es `GERENTE`: si lo es, valida que el rol/estado destino esté
   en el conjunto permitido (`ROLES_GERENTE_PERMITIDOS` =
   `{LECTOR, BIBLIOTECARIO}`; `ESTADOS_GERENTE_PERMITIDOS` =
   `{ACTIVO, INACTIVO}`) y que la cuenta objetivo haya sido creada por ese
   mismo `GERENTE` (`usuario.getCreadoPor()`).
5. Si pasa la validación (o el ejecutor es `ADMIN`, sin restricción), se
   aplica el cambio y se registra en la bitácora.

### Extensiones (flujos alternativos)

- **4a.** `GERENTE` pide un rol/estado fuera de su conjunto permitido, o
  sobre una cuenta que no creó él mismo: `AccessDeniedException`, la
  operación se rechaza tras pasar el `@PreAuthorize` del endpoint (la
  restricción vive en el service, no en la anotación del controller).
- **1a.** Rol distinto de `ADMIN`/`GERENTE`: `403` directamente en el
  `@PreAuthorize` del endpoint, sin llegar al service.
- **3a.** `DELETE /api/v1/admin/usuarios/{id}` (baja lógica a `INACTIVO`)
  con rol `GERENTE`: `403` — es la única de las cinco rutas de este
  módulo restringida a `ADMIN` desde el propio `@PreAuthorize`.
- **Nota de honestidad verificada en código (2026-09-07)**: versiones
  anteriores de la documentación de este módulo asumían que `GERENTE` era
  de solo lectura sobre el padrón. El código real (`UsuarioAdminController.java`)
  permite a `GERENTE` ejecutar las rutas de escritura a nivel de endpoint;
  el alcance limitado se aplica en `UsuarioAdminService`, no en el
  `@PreAuthorize`. Ver REQ-F-023 en el SRS para el detalle completo.
