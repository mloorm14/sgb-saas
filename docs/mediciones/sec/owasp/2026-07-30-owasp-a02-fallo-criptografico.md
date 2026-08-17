# Evidencia — OWASP A02:2021 Fallos criptográficos (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:24:32Z
- **Commit**: `6d41b88`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2 de la guía: verificar la postura del proyecto frente a
fallos criptográficos (transporte y almacenamiento de secretos).

## Limitación honesta del entorno — declarada explícitamente, no simulada

**El stack de desarrollo/evaluación local corre sobre HTTP plano, sin
TLS.** `docker-compose.yml` expone `backend` en `8080` y `frontend` en
`4200` sin ningún proxy TLS ni certificado, autofirmado o real. No hay
forma honesta de "verificar TLS" en este entorno porque **no existe
TLS activo** — cualquier evidencia que mostrara un candado o un
`https://` funcionando aquí sería fabricada. Esto se documenta como
**gap conocido, no como algo verificado y resuelto**.

Este gap es esperado y aceptado en esta etapa: la guía exige un
entorno **públicamente accesible con TLS real** recién en la Entrega
Final, no en esta Tercera Entrega. Queda pendiente para ese momento
(certificado real, ej. Let's Encrypt, o un proxy TLS delante del
stack — decisión de despliegue fuera del alcance de este archivo de
evidencia).

## Lo que SÍ se puede verificar hoy: el código está preparado para TLS

Aunque el atributo `Secure` de una cookie no tiene efecto real sin
HTTPS activo (el navegador lo ignora/no aplica la restricción sobre
HTTP), se verificó que el código **ya declara** ese atributo — es
evidencia de que, el día que el backend se sirva detrás de TLS, la
protección se activa sin cambios de código adicionales.

## Metodología / comando ejecutado

```bash
curl --include -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"usuarioA.owasp@sgb-saas.local","password":"ClaveSegura123!"}'
```

## Resultados crudos

Cabecera `Set-Cookie` completa de la respuesta de `/api/auth/login`,
sin editar (accessToken/refreshToken truncados solo en este párrafo de
cita, el archivo real de la corrida los muestra completos):

```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Set-Cookie: refreshToken=eyJhbGciOiJIUzI1NiJ9...; Path=/api/auth; Max-Age=604800; Expires=Thu, 06 Aug 2026 19:24:25 GMT; Secure; HttpOnly; SameSite=Strict
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Thu, 30 Jul 2026 19:24:25 GMT

{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","expiresIn":3600,"tokenType":"Bearer"}
```

Fuente en código de los 3 atributos (`backend-springboot/src/main/java/com/uteq/backend/controller/AuthController.java`,
método `buildRefreshCookie`):

```java
return ResponseCookie.from(REFRESH_COOKIE_NAME, valor)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/api/auth")
        .maxAge(Duration.ofMillis(maxAgeMs))
        .build();
```

## Análisis breve

Confirmado: `Secure`, `HttpOnly` y `SameSite=Strict` aparecen los 3 en
la cookie del `refreshToken`, tal como se declaran en
`AuthController.buildRefreshCookie()` — no es una casualidad de
`curl` ni de la corrida, es el código fuente el que fija esos 3
atributos incondicionalmente en cada `/login`/`/refresh`/`/logout`.
`curl` (y cualquier navegador sobre HTTP) muestra/acepta el atributo
`Secure` en la cabecera igual, pero un navegador real **no reenviaría**
esta cookie sobre una conexión HTTP no seguros — eso es exactamente la
protección que el atributo da una vez que exista TLS real.

**Contraseñas**: BCrypt costo 12 (`SecurityConfig.passwordEncoder()`),
verificado indirectamente en sesiones anteriores (`docs/adr/ADR-...`,
hash real capturado en `db/seed.sql` con el comentario `$2a$12$...`) —
no se repite esa evidencia acá para no duplicar.

**`accessToken`**: sigue viajando en el cuerpo JSON (no en cookie), tal
como documenta `docs/adr/adr-007-cookies-jwt.md` — es una decisión
deliberada y ya evaluada en ese ADR, no un descuido de esta prueba.

## Estado: GAP CONOCIDO (TLS) + evidencia parcial PASA (preparación del código para TLS)

- **TLS en tránsito**: GAP CONOCIDO — no hay TLS activo en este entorno
  de desarrollo; explícitamente fuera de alcance hasta la Entrega
  Final (entorno públicamente accesible).
- **Preparación del código para TLS** (atributo `Secure` en cookies
  sensibles): PASA — declarado y verificado en la respuesta real del
  servidor.
