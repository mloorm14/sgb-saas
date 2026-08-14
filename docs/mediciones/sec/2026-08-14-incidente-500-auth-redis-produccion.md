# Incidente — HTTP 500 en /api/auth/* (login, registro, verificación) en producción — dependencia Redis caída

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-14 (descubrimiento y diagnóstico)
- **Rama**: `feature/seguridad-headers-owasp-zap`
- **Backend**: `https://sgb-backend-b058.onrender.com` (Render Web Service, plan Free)
- **Descubierto durante**: verificación en vivo de OWASP A07 (Bloque C.2) previa al archivo de evidencia de esta rama
- **Cliente**: `curl.exe 8.x` (Windows) desde la máquina de desarrollo

## Resumen

El flujo de autenticación de producción devolvía `500 Internal Server Error`
en **todos** los endpoints que tocan Redis/Upstash (`/api/auth/login`,
`/api/auth/registro`, `/api/auth/verificar-correo`), mientras que los
endpoints que no tocan Redis respondían correctamente. El diagnóstico
aísla el fallo en la **dependencia Redis (Upstash)**: la instancia no
responde a las operaciones de datos desde el backend (causa raíz a
confirmar en el dashboard de Upstash — ver [Acciones requeridas fuera del
repo](#acciones-requeridas-fuera-del-repo)).

El código, además, tenía un problema real: **una caída de Redis producía
500 con el mensaje engañoso "Error interno del servidor"** (porque
`RedisConnectionFailureException`/`RedisSystemException` son
`DataAccessException` y caían en el handler genérico de procedimientos
almacenados), y **tumbaba la autenticación de TODAS las requests con
token** (el `catch (Exception)` del `JwtAuthFilter` limpiaba el contexto
de seguridad → 403 masivo). Ambas fallas se corrigieron con degradación
controlada (ver [Correcciones aplicadas](#correcciones-aplicadas)).

## Evidencia cruda (matriz de verificación)

| Endpoint | Entrada | Respuesta esperada | Respuesta real |
|---|---|---|---|
| `POST /api/auth/login` | credenciales demo válidas (`u@uteq.edu.ec` / `usuario1`) | `200` + token | `500` |
| `POST /api/auth/login` | contraseña incorrecta | `401` | `500` |
| `POST /api/auth/login` | usuario inexistente | `401` | `500` |
| `POST /api/auth/registro` | usuario nuevo (`auditor.zap.prod@sgb.local`) | `201/200` | `500` |
| `POST /api/auth/verificar-correo` | código basura `000000` | `400` | `500` |
| `POST /api/auth/refresh` | sin cookie `refreshToken` | `400` | `400` ✓ |
| `GET /api/v1/prestamos/usuario/1` | sin token | `403` | `403` ✓ |
| `GET /actuator/health` | — | `200` | `200` ✓ (liveness/readiness UP) |

Salidas crudas representativas:

```bash
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"u@uteq.edu.ec","password":"usuario1"}'
# {"detail":"Error interno del servidor","instance":"/api/auth/login","status":500,"title":"Internal Server Error"}

curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/verificar-correo -H "Content-Type: application/json" \
  -d '{"correo":"auditor.zap.prod@sgb.local","codigo":"000000"}'
# {"detail":"Error interno del servidor","instance":"/api/auth/verificar-correo","status":500,"title":"Internal Server Error"}
```

## Análisis de causa raíz

1. **Los 4 endpoints que fallan tocan Redis** (`RedisTemplate`) en su
   primera operación:
   - `login()`: `LoginRateLimiter.estaBloqueado()` → `GET` (línea 48 de
     `LoginRateLimiter.java`) — falla antes incluso de autenticar.
   - `registro()`: `VerificacionCorreoService.generarYEnviarCodigo()` →
     `SET` del código.
   - `verificar-correo()`: `VerificacionCorreoService.validar()` → `GET`
     (primera línea del método, antes de tocar Postgres).
   - `logout()` y toda request autenticada: `JwtAuthFilter` → `hasKey`
     de la blacklist.
2. **Los endpoints que no tocan Redis responden bien**: `refresh` sin
   cookie (400 por validación de DTO) y el filtro de seguridad A01
   (403 sin token) no pasan por Redis.
3. **`/actuator/health` responde UP**: los health indicators de
   liveness/readiness no revelan el estado de Redis en este despliegue
   (el detalle por componente no está expuesto); el health de la BD
   (Neon) responde — el problema no es Postgres.
4. **Qué lanza el 500 en el código (bug propio)**: tanto
   `RedisConnectionFailureException` (fallo de conexión) como
   `RedisSystemException` (error de protocolo/RESP, ej. cuota de comandos
   agotada en Upstash) extienden `DataAccessException`, que cae en
   `GlobalExceptionHandler.handleStoredProcedureError(...)` → al no haber
   `SQLException` en la cadena de causas, devuelve 500 genérico con el
   mensaje engañoso "Error no controlado en procedimiento almacenado".

**Causa raíz probable (dependencia, a confirmar en dashboard)**: la
instancia Upstash de producción no está sirviendo comandos de datos.
Candidatos, en orden de probabilidad:

1. **Cuota de comandos mensual del plan Free agotada** (500k
   comandos/mes): Upstash responde error a los comandos de datos; el
   backend los recibe como fallo de ejecución.
2. Token de conexión (password) revocado/rotado en Upstash sin
   actualizar `REDIS_PASSWORD` en Render.
3. Endpoint/red de Upstash con problema transitorio.

Ninguna de las tres es verificable desde este repo (requiere el
dashboard de Upstash). Ver [Acciones requeridas fuera del
repo](#acciones-requeridas-fuera-del-repo).

## Correcciones aplicadas

Degradación controlada en el código (la dependencia sigue siendo un
requisito de funcionamiento completo, pero una caída deja de producir
500 engañosos y de tumbar el flujo central de la demo):

| Archivo | Cambio | Semántica ante caída de Redis |
|---|---|---|
| `security/LoginRateLimiter.java` | los 4 métodos envuelven Redis en try/catch | **fail-open**: sin bloqueo ni contadores; el login funciona (rate limit momentáneamente ciego, log warn) |
| `security/ChatbotRateLimiter.java` | ídem (2 métodos) | **fail-open**: el chatbot sigue respondiendo sin contador de costo |
| `security/JwtAuthFilter.java` | `hasKey` de blacklist en método propio con try/catch | **fail-open acotado**: la revocación se degrada (el `exp` del JWT sigue siendo el límite duro); antes, el catch general limpiaba el contexto y TODA request autenticada daba 403 |
| `service/AuthService.java` | `logout()` (blacklist) y `registrarAuditoria()` envueltos | **best-effort**: la revocación y la bitácora se degradan a log; el login/logout no se rompen |
| `service/VerificacionCorreoService.java` | `generarYEnviarCodigo()` → 503; `validar()` → 400 | **fail-CLOSED en validación** (sin Redis no se puede comprobar el código; aceptar a ciegas sería un bypass) y **503 honesto en emisión** (el registro no debe "tener éxito" con un código que no existe en ningún lado) |
| `service/ServicioTemporalmenteNoDisponibleException.java` (nuevo) | excepción dedicada | mapeada a `503 Service Unavailable` |
| `exception/GlobalExceptionHandler.java` | handlers para `ServicioTemporalmenteNoDisponibleException` (503), `DataAccessResourceFailureException` y `UncategorizedDataAccessException` (503) | red de seguridad: cualquier fallo de dependencia de datos no envuelto (ej. `@Cacheable` de `LibroService`) responde 503 honesto, no 500 |

Nota de alcance: los caminos `@Cacheable` de `LibroService` (listados y
sugerencias) NO se degradaron a fail-open; durante una caída responden
`503` (honesto y diagnosticable). La restauración es del proveedor de la
dependencia, no del código.

## Acciones requeridas fuera del repo

1. Dashboard de Upstash → revisar la página de uso/cuota de la base de
   producción (plan Free: 500k comandos/mes; si está agotada, esperar al
   reset mensual o subir de plan).
2. Si la cuota está OK: verificar que `REDIS_PASSWORD` en Render sigue
   siendo el token vigente de Upstash (revocación/rotación rompería la
   conexión de datos sin romper el health check de la app).
3. Rearrancar el Web Service de Render (Redeploy) para descartar
   conexiones/estado de pool viciados.

## Checklist de re-verificación post-restauración (y post-deploy de esta rama)

```bash
# login con credenciales demo -> 200 + tokens
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"u@uteq.edu.ec","password":"usuario1"}'

# login con contraseña incorrecta -> 401
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"u@uteq.edu.ec","password":"ClaveIncorrecta1"}'

# login con usuario inexistente -> 401 (mismo mensaje, sin enumeración)
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"noexiste.prod@sgb.local","password":"ClaveIncorrecta1"}'

# registro de usuario nuevo -> 201/200 (o 503 honesto si la dependencia sigue caída)
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Auditor","apellido":"Zap","correo":"auditor.zap.prod@sgb.local","password":"ClaveSegura123!"}'

# verificación con código basura -> 400
curl -s -X POST https://sgb-backend-b058.onrender.com/api/auth/verificar-correo -H "Content-Type: application/json" \
  -d '{"correo":"auditor.zap.prod@sgb.local","codigo":"000000"}'
```

## Estado

- **Código**: corregido (degradación controlada) — pendiente de deploy.
- **Dependencia Redis (Upstash)**: causa raíz probable a confirmar y
  restaurar desde el dashboard de Upstash — **no resoluble desde este
  repo**. Hasta que se restaure, los flujos de autenticación siguen sin
  funcionar en producción (ahora con 503/400 honestos tras el deploy, en
  vez de 500 engañosos).
- **Estado de la evidencia OWASP**: no se archiva como "verificado" hasta
  pasar el checklist de re-verificación; el hallazgo queda registrado acá
  como incidente abierto (no oculto).
