## CU-AUTH-03: Cerrar sesión

- **Actor principal**: Usuario con sesión iniciada (cualquier rol).
- **Interesados y sus intereses**:
  - Usuario: quiere que su token deje de servir de inmediato al cerrar
    sesión, sin tener que esperar a que expire por sí solo.
  - Responsable de seguridad: quiere que el diseño stateless de JWT
    (sin sesión en servidor, ver `ADR-003-jwt-redis.md`) no impida
    invalidar un token comprometido o cerrado explícitamente.
- **Precondiciones**: el usuario tiene un `accessToken` vigente.
- **Garantía de éxito (postcondición)**: el JTI del `accessToken` queda
  en la blacklist de Redis (`blacklist:{jti}`) con TTL igual al tiempo
  restante hasta su expiración natural — cualquier request posterior
  con ese token es rechazado aunque el token en sí siga siendo
  criptográficamente válido; la cookie `refreshToken` se limpia
  (`maxAge=0`); se registra el evento `LOGOUT`.
- **Disparador**: el usuario hace logout
  (`POST /api/auth/logout`, header `Authorization: Bearer <token>`).

### Escenario principal (flujo básico)

1. El usuario solicita cerrar sesión.
2. El sistema extrae el `jti`, la fecha de expiración y el correo del
   `accessToken` recibido.
3. El sistema calcula el tiempo restante hasta la expiración natural
   del token.
4. Si el tiempo restante es mayor a 0, el sistema agrega `blacklist:{jti}`
   a Redis con ese TTL exacto (nunca más tiempo del que el token
   habría durado de todas formas).
5. El sistema limpia la cookie `refreshToken` y registra el evento
   `LOGOUT` (logs + `bitacora_auditoria`), y responde `204 No Content`.

### Extensiones (flujos alternativos)

- **3a.** El token ya venció (tiempo restante ≤ 0): el sistema no lo
  agrega a la blacklist — ya es inválido por expiración natural, no
  hace falta gastar memoria en Redis para eso.

### Nota — por qué esto cubre el NFR de arquitectura stateless (ADR-003)

Este caso de uso es la contraparte funcional de la decisión de
`ADR-003-jwt-redis.md`: el sistema es stateless por diseño (no guarda
sesiones en el servidor), pero eso por sí solo no permite invalidar un
token antes de que expire. La blacklist en Redis es el mecanismo que
resuelve esa tensión sin abandonar el diseño stateless — cada entrada
tiene TTL propio y desaparece sola cuando el token habría expirado de
todas formas, sin necesidad de limpieza manual.
