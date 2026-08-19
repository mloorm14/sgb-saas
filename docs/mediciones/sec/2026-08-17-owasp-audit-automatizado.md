# Evidencia — Re-verificación automatizada OWASP A01/A03/A07/A09 (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-17T07:05:46Z
- **Commit**: `ec83ee5`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Referencia — esto complementa, no reemplaza, la evidencia manual original

Este archivo es una **re-verificación automatizada** (`scripts/owasp-audit.sh`,
`make audit`) de 4 controles OWASP ya documentados manualmente en corridas
anteriores. Reproduce la MISMA metodología/casos de prueba de cada archivo
original citado abajo; el único cambio estructural es que
`feature/notificaciones-y-verificacion` (mergeada a main) ahora exige
verificar el correo antes de poder loguearse (`PENDIENTE_VERIFICACION` ->
bloqueo `403` hasta `POST /api/auth/verificar-correo`), así que el
"registro directo -> login directo" que usaban los scripts originales se
reemplaza aquí por `registrar_y_verificar()` (registro -> leer código de
Redis -> verificar-correo -> login) en cada punto donde antes había login
directo tras un registro.

**No reemplaza ni edita** los archivos originales:

- [`2026-07-30-owasp-a01-control-acceso-roto.md`](2026-07-30-owasp-a01-control-acceso-roto.md)
- [`2026-07-30-owasp-a03-inyeccion.md`](2026-07-30-owasp-a03-inyeccion.md)
- [`2026-07-30-owasp-a07-fix-rate-limiting-login.md`](2026-07-30-owasp-a07-fix-rate-limiting-login.md)
  (se reproduce la versión YA corregida -- rate limiting existe en el
  código actual; el gap original pre-fix queda en
  `2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md`)
- [`2026-07-30-owasp-a09-fix-logging-autenticacion.md`](2026-07-30-owasp-a09-fix-logging-autenticacion.md)
  (se reproduce la versión YA corregida -- el logging de autenticación
  existe en el código actual; el gap original pre-fix queda en
  `2026-07-30-owasp-a09-fallo-registro-monitoreo.md`)

**A02 (TLS) y A05 (CSP) quedan fuera de este script**: A02 depende de un
despliegue público que todavía no existe, y A05 corresponde a
`feature/seguridad-transporte` (rama aún sin mergear) -- no se duplica ese
trabajo aquí.

Usuarios de prueba de esta corrida: sufijo `.audit.TIMESTAMP` (distinto al
sufijo `.owasp` de las corridas manuales originales), para no chocar con
usuarios ya existentes en la base de desarrollo.

## A01 — Control de acceso roto

Usuario A id=`131` (usuarioA.audit.1786950344@sgb-saas.local), Usuario B id=`132` (usuarioB.audit.1786950344@sgb-saas.local).

**A lee los préstamos de B (`GET /api/v1/prestamos/usuario/132`)** — esperado `403`:
```
HTTP_STATUS:403
{"detail":"No tiene permisos para realizar esta acción.","instance":"/api/v1/prestamos/usuario/132","status":403,"title":"Forbidden"}
```

**A lee sus propios préstamos (`GET /api/v1/prestamos/usuario/131`, control)** — esperado `200`:
```
HTTP_STATUS:200
{"content":[],"empty":true,"first":true,"last":true,"number":0,"numberOfElements":0,"pageable":{"offset":0,"pageNumber":0,"pageSize":10,"paged":true,"sort":{"empty":false,"sorted":true,"unsorted":false},"unpaged":false},"size":10,"sort":{"empty":false,"sorted":true,"unsorted":false},"totalElements":0,"totalPages":0}
```

**Resultado: PASA**

## A03 — Inyección

**Caso 1 — payload en `correo` de login** — esperado `400` (rechazado por `@Email`, nunca llega a una consulta):
```
HTTP_STATUS:400
{"detail":"Datos inválidos","instance":"/api/auth/login","status":400,"title":"Bad Request","errores":{"correo":"must be a well-formed email address"}}
```

**Caso 2 — payload en `nombre`/`apellido` de registro (incluye intento de `DROP TABLE`)** — esperado `201` (se guarda literal como texto):
```
HTTP_STATUS:201
{"id":133,"nombre":"' OR '1'='1","correo":"usuarioC.audit.1786950344@sgb-saas.local","roles":["LECTOR"]}
```

**Verificación de integridad** (login del usuario A, registrado y verificado antes del payload) — esperado `200`:
```
HTTP_STATUS:200
```

**Resultado: PASA**

## A07 — Fallos de identificación y autenticación (rate limiting de login)

**6 intentos fallidos consecutivos** contra `usuarioA07.audit.1786950344@sgb-saas.local` — esperado `401` en los primeros 5, `429` en el sexto:
```
intento 1: 401
intento 2: 401
intento 3: 401
intento 4: 401
intento 5: 401
intento 6: 429
```

**Verificación de reseteo del contador** (usuario `usuarioReseteo.audit.1786950344@sgb-saas.local`): 2 fallos, luego login correcto.
```
clave Redis: login-attempts:usuarioReseteo.audit.1786950344@sgb-saas.local:172.18.0.1
contador antes del login exitoso: 2
HTTP_STATUS login exitoso: 200
contador después del login exitoso: (vacío)
```

**Resultado: PASA**

## A09 — Fallos de registro y monitoreo (logging de autenticación)

Usuario `usuarioA09.audit.1786950344@sgb-saas.local` (id=`136`): 2 logins fallidos + 1 login exitoso + 1 logout.

**Logs de aplicación (`docker logs sgb_backend`, filtrados por AuthService + correo de esta corrida):**
```
2026-08-17T07:06:04.579Z  WARN 1 --- [backend] [io-8080-exec-14] com.uteq.backend.service.AuthService     : Login fallido: correo=usuarioA09.audit.1786950344@sgb-saas.local ip=172.18.0.1
2026-08-17T07:06:04.849Z  WARN 1 --- [backend] [io-8080-exec-13] com.uteq.backend.service.AuthService     : Login fallido: correo=usuarioA09.audit.1786950344@sgb-saas.local ip=172.18.0.1
2026-08-17T07:06:05.124Z  INFO 1 --- [backend] [nio-8080-exec-4] com.uteq.backend.service.AuthService     : Login exitoso: sub=136 correo=usuarioA09.audit.1786950344@sgb-saas.local ip=172.18.0.1
2026-08-17T07:06:05.303Z  INFO 1 --- [backend] [nio-8080-exec-7] com.uteq.backend.service.AuthService     : Logout: correo=usuarioA09.audit.1786950344@sgb-saas.local jti=63d46b94-f13a-47e6-983d-4b040dd1658a ip=172.18.0.1
```

**`bitacora_auditoria` (filtrada por este correo en `detalles`):**
```
LOGIN_FAIL
LOGIN_FAIL
LOGIN_OK
LOGOUT
```

**Resultado: PASA**

