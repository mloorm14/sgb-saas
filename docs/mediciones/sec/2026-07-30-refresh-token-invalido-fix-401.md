# Evidencia — Fix: refresh con token inválido respondía 500 en vez de 401

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T22:27:51Z
- **Commit**: `3db1a96` (backend reconstruido con este fix inmediatamente después)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Contexto

Documentado como limitación conocida en
`docs/requisitos/casos-de-uso/CU-AUTH-04-refrescar-sesion.md` al
escribirse: `AuthService.refresh()` lanzaba una `RuntimeException`
genérica cuando el `refreshToken` de la cookie era inválido/expirado,
o cuando el correo que codifica ya no resolvía a un usuario existente.
Sin un `@ExceptionHandler` dedicado, `GlobalExceptionHandler` la
capturaba con el handler genérico (`handleGenerica`) y respondía
`500 Internal Server Error` — semánticamente incorrecto, ya que el
problema real es una sesión no autorizada (`401`), no un error interno
del servidor.

## Fix aplicado

- Nueva excepción `RefreshTokenInvalidoException` (`RuntimeException`,
  mismo patrón que `CorreoYaRegistradoException`/
  `LoginRateLimitExcedidoException`).
- `AuthService.refresh()` la lanza en ambos casos (token
  inválido/expirado, y usuario no encontrado) con el mismo mensaje
  genérico — no distingue cuál de los dos fue, para no filtrar si una
  cuenta existe o no.
- Nuevo `@ExceptionHandler(RefreshTokenInvalidoException.class)` en
  `GlobalExceptionHandler` que responde `401 Unauthorized` vía
  `ProblemDetail`.

## Metodología / comandos ejecutados

Backend reconstruido con el fix:
```bash
docker compose up -d --build backend
```

Caso 1 — sin cookie (ya era `400`, confirmar que sigue igual):
```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8080/api/auth/refresh
```

Caso 2 — cookie con un valor que no es un JWT válido (antes `500`):
```bash
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8080/api/auth/refresh \
  --cookie "refreshToken=esto-no-es-un-jwt-valido"
```

Caso 3 — regresión: cookie real obtenida de un login exitoso (confirmar
que el flujo legítimo sigue funcionando sin cambios):
```bash
RESP=$(curl -s -i -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"adminfix.owasp@sgb-saas.local","password":"ClaveSegura123!"}')
COOKIE=$(echo "$RESP" | grep -i "^set-cookie: refreshToken" | sed -E 's/^set-cookie: ([^;]+);.*/\1/i')
curl -s -w "\n%{http_code}\n" -X POST http://localhost:8080/api/auth/refresh --cookie "$COOKIE"
```

## Resultados crudos

| Caso | Antes del fix | Después del fix |
|---|---|---|
| Sin cookie | `400` | **`400`** (sin cambios) |
| Cookie con valor inválido | `500` | **`401`** |
| Cookie real y válida (regresión) | `200` | **`200`** (sin cambios) |

Body real del caso 2 (`401`):
```json
{"detail":"Refresh token inválido o expirado. Inicie sesión nuevamente.","instance":"/api/auth/refresh","status":401,"title":"Unauthorized"}
```

Body real del caso 3 (`200`, confirma que el flujo legítimo no se rompió):
```json
{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","expiresIn":3600,"tokenType":"Bearer"}
```

## Análisis breve

El caso 2 es la prueba clave: antes del fix, un `refreshToken`
manipulado o corrupto producía un `500` genérico (`{"detail":"Error
interno del servidor",...}`), indistinguible para un cliente de un
error real del backend. Ahora responde `401` con un mensaje accionable
("inicie sesión nuevamente"), consistente con el resto de los errores
de autenticación del sistema (`401` en credenciales inválidas, `423`
en cuenta bloqueada, etc.), todos vía `ProblemDetail`.

El caso de "usuario no encontrado" (token estructuralmente válido pero
el correo ya no existe) no se reprodujo en vivo porque requeriría
generar un JWT firmado con el secreto real de la aplicación para un
usuario ya eliminado — cubierto en cambio por
`AuthServiceTest.refreshConUsuarioNoEncontrado_lanzaRefreshTokenInvalidoException`
(prueba unitaria con `JwtService` mockeado).

## Estado: PASA (gap cerrado)

Antes: un `refreshToken` inválido/corrupto respondía `500`. Ahora
responde `401` con `ProblemDetail`, sin afectar los flujos de éxito ni
el caso de cookie ausente. Verificado en vivo contra el stack Docker
real.
