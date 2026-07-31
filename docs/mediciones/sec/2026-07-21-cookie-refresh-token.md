# Evidencia — cookie HttpOnly+Secure+SameSite=Strict del refreshToken

## Cabecera de medición

<!-- Retro-ajustada al formato de scripts/mediciones-header.sh (el script
no existía cuando se generó esta evidencia originalmente el 2026-07-21;
los valores de abajo son los reales de esa corrida, solo se homogeneizó
el formato, no el contenido). -->
- **Fecha (ISO 8601 UTC)**: 2026-07-21T11:41:23Z (timestamp real tomado del
  header `Date` de la respuesta de login en la sección 1 de abajo; la
  ventana completa de comandos de este archivo corre entre 11:41:04Z y
  11:45:49Z el mismo día)
- **Commit**: `1dfc4f8` (`feat(security): cookie HttpOnly+Secure+SameSite=Strict
  para refreshToken` — este mismo archivo se agregó en ese commit)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" (Eclipse Temurin)
- **Maven**: Apache Maven 3.9.12
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14 (imagen `postgres:16-alpine`)
- **Redis** (contenedor `sgb_redis`): 7.4.9 (imagen `redis:7-alpine`)
- **curl**: 8.18.0 (libcurl/8.18.0) — herramienta adicional usada en esta
  evidencia, fuera del set fijo del script (login/refresh/logout vía HTTP)

## Contexto

**Entorno**: stack Docker Compose local (`docker compose up -d --build backend`,
sin volumen limpio — solo se reconstruyó/reinició el servicio `backend`
tras los cambios de código). Backend arrancó healthy, Flyway validó 3
migraciones sin errores (`Current version of schema "public": 2`).
**Propósito**: evidencia cruda para Bloque C.2 (auditoría OWASP, control
A02 — Cryptographic Failures / exposición de secretos de sesión) y para el
requisito A.1 de la guía (autenticación bajo cookie HttpOnly+Secure+SameSite=Strict).
**Contexto de la corrección**: al revisar `AuthController`/`AuthService`
antes de este cambio se confirmó que el `refreshToken` NO usaba cookie en
absoluto (viajaba en texto plano en el cuerpo JSON, sin protección alguna;
ver `docs/adr/adr-007-cookies-jwt.md` para el detalle completo de esta
corrección de premisa). Toda la evidencia de abajo es POSTERIOR a la
implementación de la cookie.

Los tokens JWT que aparecen abajo están firmados con el `JWT_SECRET` de
desarrollo (placeholder de `.env`, `CAMBIAR_EN_PRODUCCION_MIN_256_BITS` /
equivalente local), correspondientes a una base de datos de desarrollo
sembrada con el usuario admin de `db/seed.sql`. No son secretos de
producción.

## 1. POST /api/auth/login — Set-Cookie con los 3 atributos requeridos

Comando:
```
curl -s -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"admin@sgb-saas.local","password":"Admin123!"}'
```

Respuesta completa (`curl -i`, incluye cabeceras):
```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Set-Cookie: refreshToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiY29ycmVvIjoiYWRtaW5Ac2diLXNhYXMubG9jYWwiLCJyb2xlcyI6WyJBRE1JTiJdLCJyb2wiOiJBRE1JTiIsImp0aSI6IjE4NmI0NWFkLWI2ODctNDVlYS1iMTIxLTNjMzVlYmY1NzZmOSIsImlhdCI6MTc4NDYzNDA4MywiZXhwIjoxNzg1MjM4ODgzfQ.UF6YItHfXhLkUF7y4pZBjU8ZOyt-GjiQSrayxjZ49hk; Path=/api/auth; Max-Age=604800; Expires=Tue, 28 Jul 2026 11:41:23 GMT; Secure; HttpOnly; SameSite=Strict
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Tue, 21 Jul 2026 11:41:23 GMT

{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiY29ycmVvIjoiYWRtaW5Ac2diLXNhYXMubG9jYWwiLCJyb2xlcyI6WyJBRE1JTiJdLCJyb2wiOiJBRE1JTiIsImp0aSI6IjVjYzE2MGY5LWE3YzAtNDhjMi1iZmU5LTg2YmE2MmY2NzJjMCIsImlhdCI6MTc4NDYzNDA4MywiZXhwIjoxNzg0NjM3NjgzfQ.KUh7NfbXzNxXx7hcAZi1Kj-Hhy8sA1k3ybP9U2eIfko","expiresIn":3600,"tokenType":"Bearer"}
```

Verificación de los 3 atributos exigidos en el `Set-Cookie`:
- `HttpOnly` ✅ presente
- `Secure` ✅ presente
- `SameSite=Strict` ✅ presente
- Adicional: `Path=/api/auth` (la cookie no se envía a `/api/v1/**`) y
  `Max-Age=604800` (7 días, igual a `security.jwt.refresh-expiration-ms`).

Verificación del cuerpo JSON: **no contiene** `refreshToken` (solo
`accessToken`, `expiresIn`, `tokenType`) — confirma que el secreto de larga
duración ya no es legible por JavaScript vía la respuesta de `fetch`/`XHR`,
solo vive en la cookie `HttpOnly`.

## 2. POST /api/auth/refresh — usando la cookie (cookie jar de curl)

Comando (cookie jar generado por el login anterior con `curl -c`):
```
curl -s -i -b "$COOKIE_JAR" -X POST http://localhost:8080/api/auth/refresh
```

Respuesta:
```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Set-Cookie: refreshToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiY29ycmVvIjoiYWRtaW5Ac2diLXNhYXMubG9jYWwiLCJyb2xlcyI6WyJBRE1JTiJdLCJyb2wiOiJBRE1JTiIsImp0aSI6IjYwZDljODljLTJlNzEtNDFkNi05YzhhLTU0Yzc1N2NmMDEyOCIsImlhdCI6MTc4NDYzNDA5NCwiZXhwIjoxNzg1MjM4ODk0fQ.ATQypWYqGljJdUWULAmkhn3aorJOzXsdzcEkR4ZXJFU; Path=/api/auth; Max-Age=604800; Expires=Tue, 28 Jul 2026 11:41:34 GMT; Secure; HttpOnly; SameSite=Strict
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Tue, 21 Jul 2026 11:41:34 GMT

{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiY29ycmVvIjoiYWRtaW5Ac2diLXNhYXMubG9jYWwiLCJyb2xlcyI6WyJBRE1JTiJdLCJyb2wiOiJBRE1JTiIsImp0aSI6IjhjOTE2MDczLWU4NDMtNDBhMC04NzZlLWI2NmM2MjQ5OThhMiIsImlhdCI6MTc4NDYzNDA5NCwiZXhwIjoxNzg0NjM3Njk0fQ.4t7nmGRjn3MkA9UaYzqJs6l4w6KpB9x1tw6qNGMGqaE","expiresIn":3600,"tokenType":"Bearer"}
```

Un `accessToken` nuevo se emite correctamente, la cookie se reenvía con los
mismos atributos y `Max-Age` renovado.

## 3. POST /api/auth/refresh — sin cookie (caso negativo, RFC 7807)

Comando:
```
curl -s -i -X POST http://localhost:8080/api/auth/refresh
```

Respuesta:
```
HTTP/1.1 400
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Tue, 21 Jul 2026 11:41:34 GMT
Connection: close

{"detail":"Falta la cookie refreshToken","instance":"/api/auth/refresh","status":400,"title":"Bad Request"}
```

Confirma que la ausencia de la cookie produce un 400 RFC 7807 controlado
(`application/problem+json`), no un 500 sin manejar.

## 4. POST /api/auth/logout — limpia la cookie (Max-Age=0)

Comando:
```
curl -s -i -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $ACCESS"
```

Respuesta:
```
HTTP/1.1 204
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Set-Cookie: refreshToken=; Path=/api/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Date: Tue, 21 Jul 2026 11:41:44 GMT
```

El logout invalida el `accessToken` en la blacklist de Redis (comportamiento
preexistente, ver `ADR-003-jwt-redis.md`) **y además** limpia la cookie del
refresh token (`Max-Age=0`, `Expires` en el pasado) — el navegador la
elimina inmediatamente.

### Verificación explícita: el body de /logout no filtra el refreshToken

Repetido el 2026-07-22T01:29:02Z contra el mismo backend, capturando el
cuerpo de la respuesta por separado de las cabeceras
(`curl -s -D - -o body.txt -w "BODY_BYTES:%{size_download}"`):

```
HTTP/1.1 204
Set-Cookie: refreshToken=; Path=/api/auth; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict
...
Date: Wed, 22 Jul 2026 01:29:02 GMT

BODY_BYTES:0
```

`BODY_BYTES:0` — el archivo de body queda vacío (0 bytes), consistente con
`204 No Content` (que por RFC 9110 no debe llevar cuerpo). No hay ningún
resto del `refreshToken` en la respuesta de logout: no puede haberlo,
porque `AuthController.logout()` devuelve `ResponseEntity.noContent()...build()`,
sin cuerpo alguno, independientemente de `@JsonIgnore` en `TokenResponseDTO`
(ese DTO ni siquiera se construye en el flujo de logout). También se
verificó el body de `/login` en la sección 1: solo contiene `accessToken`,
`expiresIn` y `tokenType` — ningún campo `refreshToken`.

## Nota sobre el atributo `Secure` en desarrollo local

`curl` no aplica la semántica de `Secure` (la captura y la muestra
igual sobre HTTP plano, como se ve arriba: el backend expuesto en
`http://localhost:8080`). Esta evidencia confirma que el **atributo está
presente en la cabecera**, no que un navegador real la haya aceptado sobre
HTTP. En un navegador real, una cookie `Secure` solo se envía de vuelta por
HTTPS — con la excepción de `localhost`/`127.0.0.1`, que los navegadores
modernos (Chrome, Firefox) tratan como "contexto seguro" incluso sin TLS,
por lo que el flujo funciona igual en este entorno de desarrollo. En
producción, el backend debe servirse detrás de HTTPS para que el
navegador acepte y reenvíe esta cookie.
