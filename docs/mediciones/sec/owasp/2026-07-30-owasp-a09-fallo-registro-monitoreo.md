# Evidencia — OWASP A09:2021 Fallos de registro y monitoreo (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:30:11Z
- **Commit**: `72a005f`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2: verificar que el logging de login (exitoso y fallido)
capture IP, timestamp y el claim `sub` del JWT cuando aplica, revisando
los logs reales del contenedor `sgb_backend`.

## Verificación previa en código (antes de correr la prueba)

`grep` sobre `backend-springboot/src/main/java/com/uteq/backend/` por
cualquier uso de `Logger`/`log.`: solo aparece en
`GlobalExceptionHandler` (errores 500 no controlados) y
`ReservacionScheduler` (job programado). **Ni `AuthService.login()`,
ni `AuthController`, ni `JwtAuthFilter`, ni
`UserDetailsServiceImpl` tienen ninguna llamada a un logger.** Se
documenta esto antes de correr la prueba: el resultado esperado, dado
el código real, es que la ventana de tiempo de los intentos de login
no tenga ninguna línea de log relacionada.

## Metodología / comando ejecutado

```bash
date -u +%Y-%m-%dT%H:%M:%SZ   # marca de tiempo antes

curl -s -o /dev/null -w "HTTP_STATUS:%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"usuarioA.owasp@sgb-saas.local","password":"ClaveSegura123!"}'      # login EXITOSO

curl -s -o /dev/null -w "HTTP_STATUS:%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"usuarioA.owasp@sgb-saas.local","password":"claveMalIntencionadaA09"}'  # login FALLIDO

date -u +%Y-%m-%dT%H:%M:%SZ   # marca de tiempo despues

docker logs sgb_backend --since 2026-07-30T19:29:47Z
```

## Resultados crudos

**Ventana de la prueba**: `2026-07-30T19:29:48Z` (antes) →
`2026-07-30T19:29:49Z` (después). Login exitoso: `HTTP_STATUS:200`.
Login fallido: `HTTP_STATUS:401`.

**`docker logs sgb_backend --since 2026-07-30T19:29:47Z`**:
```
(sin salida — cero líneas de log en toda la ventana de la prueba)
```

**Verificación de que el contenedor sí loguea normalmente (sanity
check, `docker logs sgb_backend --tail 20` sin filtro de tiempo,
capturado en el mismo momento)** — muestra logs de arranque, un
`WARN` de Spring Security, el job del scheduler
(`19:21:05.895Z ... ReservacionScheduler : Job de expiración de
reservaciones: 0 filas actualizadas`), y un `WARN` de serialización de
`PageImpl` de una llamada anterior (`19:23:16.501Z`) — confirma que el
logging del contenedor funciona y que los timestamps están en UTC,
consistentes con `date -u`. La ausencia de líneas específicamente en
la ventana `19:29:47Z`–`19:29:49Z` no es un problema de filtro ni de
zona horaria: es que el login, exitoso o fallido, genuinamente no
produce ninguna línea de log.

## Análisis breve

**Ningún campo del requisito (IP, timestamp de evento, claim `sub`)
se captura hoy, porque no se captura absolutamente nada.** No es un
caso de "falta un campo" — es ausencia total de logging de eventos de
autenticación:

- **Login exitoso**: `AuthService.login()` autentica, genera
  `accessToken`/`refreshToken` y retorna — sin ninguna línea de log
  intermedia.
- **Login fallido**: `AuthenticationManager.authenticate(...)` lanza
  `BadCredentialsException`, capturada por
  `GlobalExceptionHandler.handleBadCredentials()` — que retorna el
  `ProblemDetail` pero **no loguea el intento** (a diferencia del
  handler genérico de 500, que sí tiene `log.error(...)`).
- **IP del cliente**: no se lee `HttpServletRequest.getRemoteAddr()`
  (ni ninguna cabecera `X-Forwarded-For`) en ningún punto del flujo de
  autenticación.
- **Claim `sub`**: existe y es correcto dentro del JWT emitido (`sub`
  = id de usuario, verificado en evidencias anteriores de esta sesión,
  ej. el payload decodificado en A01/A02), pero nunca se escribe a
  ningún log — solo viaja dentro del token hacia el cliente.

Esto es una brecha real para trazabilidad/forense: hoy no hay forma de
responder "¿quién intentó iniciar sesión, desde dónde, y cuándo?"
revisando los logs del backend — ni para investigar un incidente de
fuerza bruta (agravado por el gap ya documentado en A07) ni para
auditoría de accesos legítimos.

## Estado: GAP CONOCIDO

No implementado — ninguno de los 3 campos requeridos (IP, timestamp de
evento, `sub`) se loguea en login exitoso ni fallido. Candidato real
para un prompt de corrección independiente: agregar logging explícito
en `AuthService.login()` (éxito: `sub` + IP) y en el handler de
`BadCredentialsException` de `GlobalExceptionHandler` (fallo: correo
intentado + IP, sin loguear la contraseña en ningún caso). No se
implementa en este archivo de evidencia — es diagnóstico, no
corrección.
