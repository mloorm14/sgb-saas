## CU-AUTH-06: Registrar eventos de autenticación para auditoría

- **Actor principal**: El sistema mismo (registro automático, no hay
  acción manual de un usuario en este caso de uso).
- **Interesados y sus intereses**:
  - Administrador/auditor: quiere poder reconstruir qué pasó con una
    cuenta específica después del hecho, sin depender de que el
    usuario afectado lo reporte.
  - Usuario: quiere que, si alguien más intenta acceder a su cuenta,
    quede un rastro verificable.
- **Precondiciones**: ninguna — se registra en cada intento de login
  (exitoso o fallido) y en cada logout.
- **Garantía de éxito (postcondición)**: el evento queda en **dos**
  lugares en paralelo, para dos audiencias distintas:
  - **Logs de aplicación** (`AuthService`, nivel INFO para éxito/logout,
    WARN para fallos) — lo que un operador revisa en vivo con
    `docker logs` o un agregador de logs, sin tocar la base de datos.
  - **`bitacora_auditoria`** (tabla que ya preveía `LOGIN_OK`/
    `LOGIN_FAIL`/`LOGOUT` en su `CHECK` de `tipo_operacion` desde el
    diseño original del esquema) — el registro persistente,
    consultable con SQL para investigación posterior.
- **Disparador**: cada llamada a `POST /api/auth/login` (éxito o
  fallo) y `POST /api/auth/logout`.

### Escenario principal (flujo básico)

1. `AuthService` resuelve el resultado de la operación (login exitoso,
   login fallido, o logout).
2. El sistema escribe una línea de log con el tipo de evento, el
   correo o el `sub` (id del usuario si ya se conoce), la IP de
   origen, y el timestamp (el patrón de log por defecto de Spring Boot
   ya incluye timestamp ISO-8601, sin necesidad de configuración
   adicional).
3. El sistema inserta una fila en `bitacora_auditoria` con
   `tipo_operacion` (`LOGIN_OK`/`LOGIN_FAIL`/`LOGOUT`),
   `tabla_afectada='usuarios'`, `ip_origen`, `fecha_hora`, y
   `detalles` con el correo involucrado. La inserción es un `INSERT`
   simple de una sola tabla (sin joins ni lógica cruzada), consistente
   con la estrategia CRUD-ORM de `adr-013-acceso-datos-orm-sp.md` —
   no justifica un procedimiento almacenado.

### Extensiones (flujos alternativos)

- **Login fallido o logout**: `usuario_id` queda `NULL` en
  `bitacora_auditoria` — no se hace una consulta extra solo para
  resolverlo cuando no se tiene ya el `Usuario` cargado; el correo
  intentado queda en `detalles` para correlación manual si hace falta.
- **Login exitoso**: sí se resuelve `usuario_id`, porque el `Usuario`
  ya está cargado en ese punto del flujo, sin costo adicional.

### Nota — por qué ambas fuentes, no una en vez de la otra

Los logs de aplicación y `bitacora_auditoria` no son redundantes: uno
sirve para operación en vivo (¿qué está pasando ahora mismo?), el otro
para investigación posterior con consultas SQL (¿qué pasó con este
usuario en las últimas dos semanas?). Mantener ambos es una decisión
explícita, no un descuido.

**Evidencia empírica**: verificado en vivo contra el stack real, logs
y tabla reales —
`docs/mediciones/sec/owasp/2026-07-30-owasp-a09-fix-logging-autenticacion.md`.
