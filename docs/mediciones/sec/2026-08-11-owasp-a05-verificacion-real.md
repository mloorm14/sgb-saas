# Evidencia — OWASP A05:2021 Mala configuración de seguridad — verificación real en Docker (Módulo 10.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-08-12T04:20:00Z
- **Rama**: `main`
- **Entorno**: stack Docker real (`docker compose up -d --build`), levantado
  desde el commit `32bd7bd4259afa6a1e824752b61bb80045eddd94` de `main`.

## Referencia al gap original

Este archivo **no reemplaza ni edita**
[`2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md`](2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md).
Ese archivo (rama `feature/seguridad-transporte`, de Cajas) dejó explícito
que no pudo ejecutar la verificación real por no tener Docker disponible en
su entorno de edición, y dejó los 4 comandos exactos pendientes "para quien
integre esta rama". Este documento ejecuta esos 4 comandos contra el stack
real y documenta de forma transparente lo que ocurrió, incluyendo un
hallazgo real que forzó un fix adicional (ver más abajo).

## Los 4 comandos y su salida real

### 1. Usuario del contenedor backend (no root)

```
$ docker exec sgb_backend whoami
spring
```

**Esperado**: `spring` (no `root`). **Resultado**: ✅ coincide.

### 2. Header `Content-Security-Policy` presente

```
$ curl -I -s http://localhost:8080/actuator/health | grep -i content-security-policy
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'
```

**Esperado**: header presente con la política declarada en
`SecurityConfig.java`. **Resultado**: ✅ coincide.

### 3. Perfil `prod` activo (sin tumbar Postgres/Redis)

Activado recreando solo el contenedor `backend` con la variable de entorno,
dejando `postgres`/`redis` corriendo sin interrupción:

```
$ SPRING_PROFILES_ACTIVE=prod docker compose up -d backend
...
$ docker compose logs backend | grep -i "profile is active"
The following 1 profile is active: "prod"
```

**Resultado**: ✅ perfil `prod` confirmado activo.

### 4. Swagger UI inaccesible (404) con perfil `prod`

```
$ curl -s http://localhost:8080/swagger-ui.html
```

**Primer intento (antes del fix)**: ❌ **500**, no 404:

```
{"detail":"Error interno del servidor","instance":"/swagger-ui.html","status":500,"title":"Internal Server Error"}
```

**Causa raíz**: `springdoc.swagger-ui.enabled: false` en el perfil `prod`
efectivamente deshabilita Swagger (no hay ruta ni bean para servirlo), pero
Spring resuelve esa ausencia lanzando `NoResourceFoundException` — que
`GlobalExceptionHandler` no tenía mapeada a un handler específico, así que
caía en el catch-all genérico `handleGenerica(Exception ex)` y respondía
`500` en vez del `404` esperado. El efecto práctico (Swagger no accesible)
sí se cumplía, pero el código de estado no era el correcto.

**Fix aplicado**: commit `951fae5` — nuevo `@ExceptionHandler(NoResourceFoundException.class)`
en `GlobalExceptionHandler.java` que devuelve `404` vía `ProblemDetail`
(mismo patrón RFC 7807 que el resto de la clase), en vez de dejar que caiga
en el catch-all.

**Segundo intento (tras el fix, mismo stack, mismo perfil `prod`)**: ✅ **404**:

```
$ curl -s http://localhost:8080/swagger-ui.html
{"detail":"Recurso no encontrado","instance":"/swagger-ui.html","status":404,"title":"Not Found"}

$ curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/docs
404
```

Se verificó también `/api/docs` (mismo mecanismo de `springdoc`) como
control adicional: también da `404` ahora.

## Verificación de que el fix no rompió nada

- Repetición de los comandos 1-3 con el fix ya aplicado: los 3 siguen en
  verde (`spring`, header CSP presente, perfil `prod` activo).
- `./mvnw -B clean verify`: **BUILD SUCCESS**, 201 tests, 0 fallos, 0
  errores, 2 skipped (suite de integración con Gemini real, `@Disabled` por
  diseño — no relacionada con este fix).
- El contenedor `postgres` se recreó automáticamente en dos de las
  recreaciones de `backend` (comportamiento observado de Docker Compose en
  este entorno, no solicitado explícitamente) — se confirmó en ambos casos
  que el volumen `pgdata` no se perdió (`flyway_schema_history` mantuvo sus
  7 filas, versión más alta `9`, antes y después). `redis` no se recreó en
  ningún momento.
- Al terminar, el contenedor `backend` se revirtió al perfil por defecto
  (`SPRING_PROFILES_ACTIVE` sin valor), quedó `healthy`, entorno limpio.

## Estado

- Usuario no root del contenedor backend: **CERRADO** (confirmado en
  Docker real).
- `Content-Security-Policy` (backend): **CERRADO** (confirmado en Docker
  real).
- Perfil `prod` activable sin tumbar el resto del stack: **CERRADO**.
- Swagger UI / OpenAPI docs inaccesibles con `404` en `prod`: **CERRADO**
  (requirió el fix del commit `951fae5`, adicional a lo que dejó
  `feature/seguridad-transporte`; antes del fix devolvía `500`, lo cual
  seguía ocultando la superficie de Swagger pero con el código de estado
  incorrecto).

Con esto, los 4 puntos que `2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md`
dejó como "verificación pendiente" quedan confirmados con salida real.
