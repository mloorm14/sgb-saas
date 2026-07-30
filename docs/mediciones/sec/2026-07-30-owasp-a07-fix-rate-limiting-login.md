# Evidencia — OWASP A07:2021 Fallos de identificación y autenticación — GAP CERRADO (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T20:47:17Z
- **Commit**: `50d4b77`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Referencia al gap original

Este archivo **no reemplaza ni edita**
[`2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md`](2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md)
— documenta la corrección aplicada sobre ese gap, con la misma
metodología exacta usada en la auditoría original, para que ambos
archivos queden como registro histórico (antes/después) de la misma
verificación.

## Propósito

Repetir la prueba exacta de la auditoría original (6 intentos fallidos
consecutivos de login, mismo usuario, mismo origen) contra el backend
ya corregido (`LoginRateLimiter`, ver
`backend-springboot/src/main/java/com/uteq/backend/security/LoginRateLimiter.java`),
y confirmar `429` desde el sexto intento.

## Metodología / comando ejecutado

Stack reconstruido con el código corregido:
```bash
docker compose up -d --build backend
```

Usuario de prueba nuevo (evita cualquier estado previo de otras
verificaciones):
```bash
curl -s -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Carla","apellido":"RateLimit","correo":"usuarioA07fix.owasp@sgb-saas.local","password":"ClaveSegura123!"}'
```

6 intentos fallidos consecutivos, idéntico al comando de la auditoría original:
```bash
for i in 1 2 3 4 5 6; do
  curl --include -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
    -d '{"correo":"usuarioA07fix.owasp@sgb-saas.local","password":"ClaveIncorrecta'"$i"'"}'
done
```

Verificación adicional de la clave real en Redis:
```bash
docker exec sgb_redis redis-cli KEYS "login-attempts:*"
docker exec sgb_redis redis-cli GET "login-attempts:usuarioA07fix.owasp@sgb-saas.local:172.18.0.1"
docker exec sgb_redis redis-cli TTL "login-attempts:usuarioA07fix.owasp@sgb-saas.local:172.18.0.1"
```

Verificación de reseteo (usuario distinto, para no interferir con el
contador ya en el máximo del anterior): 2 fallos + 1 login exitoso,
comparando el contador antes/después.

## Resultados crudos

**Intentos 1 a 5 — sin cambios respecto al gap original (401):**
```
--- intento 1 ---
HTTP/1.1 401
...
{"detail":"Credenciales inválidas","instance":"/api/auth/login","status":401,"title":"Unauthorized"}
(idéntico en los intentos 2, 3, 4 y 5)
```

**Intento 6 — antes daba 401, ahora:**
```
HTTP/1.1 429
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
Date: Thu, 30 Jul 2026 20:44:34 GMT

{"detail":"Demasiados intentos fallidos. Intente nuevamente en 898 segundos.","instance":"/api/auth/login","status":429,"title":"Too Many Requests"}
```

**Clave real en Redis, confirmando el diseño correo+IP:**
```
$ docker exec sgb_redis redis-cli KEYS "login-attempts:*"
login-attempts:usuarioA07fix.owasp@sgb-saas.local:172.18.0.1

$ docker exec sgb_redis redis-cli GET "login-attempts:usuarioA07fix.owasp@sgb-saas.local:172.18.0.1"
5

$ docker exec sgb_redis redis-cli TTL "login-attempts:usuarioA07fix.owasp@sgb-saas.local:172.18.0.1"
884
```

**Reseteo por login exitoso (usuario `usuarioReseteo.owasp@sgb-saas.local`):**
```
2 intentos fallidos -> HTTP 401, 401
GET del contador tras los 2 fallos: "2"
Login con contraseña correcta -> HTTP 200
GET del contador después del éxito: (vacío — la clave ya no existe)
```

## Análisis breve

**El contador nunca superó `5`** (queda en `5`, no `6`): el sexto
intento se rechaza *antes* de incrementar, exactamente como pide el
diseño ("verifica el contador antes de intentar autenticar... si ya
alcanzó el máximo, responde 429 sin siquiera intentar la
autenticación") — confirmado también porque el `TTL` capturado
(`884`s) es coherente con la ventana de `900`s fijada en el *primer*
fallo, no reiniciada en cada intento posterior.

**Diseño anti-DoS confirmado por la clave real observada**: la clave
en Redis es `login-attempts:<correo>:<ip>`, no solo `<correo>` — un
atacante fallando login contra el correo de otra persona desde IPs
propias incrementa contadores con *su propia IP* en la clave, nunca el
contador de la IP real de la víctima. La víctima conserva su cupo de
intentos intacto en todo momento. Limitación aceptada y ya documentada
en el propio código (`LoginRateLimiter.java`): un atacante con
múltiples IPs puede seguir intentando fuerza bruta rotando de IP cada
5 intentos — mitigar eso (reputación de IP, CAPTCHA) queda fuera de
alcance de esta corrección.

**Reseteo confirmado en vivo, no solo por inspección de código**: el
contador pasó de `"2"` a inexistente inmediatamente después de un
login correcto — un usuario que se equivoca y luego acierta no
arrastra fallos previos hacia un futuro bloqueo.

## Estado: PASA (gap cerrado)

Antes: 6/6 intentos fallidos devolvían `401` sin ningún límite.
Ahora: intentos 1–5 devuelven `401`, intento 6 devuelve `429` con
`ProblemDetail` y tiempo de espera informado. Verificado en vivo
contra el stack Docker real, mismo comando que la auditoría original.
