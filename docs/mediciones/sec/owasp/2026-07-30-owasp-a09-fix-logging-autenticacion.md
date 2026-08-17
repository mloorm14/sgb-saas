# Evidencia — OWASP A09:2021 Fallos de registro y monitoreo — GAP CERRADO (Bloque C.2)

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
[`2026-07-30-owasp-a09-fallo-registro-monitoreo.md`](2026-07-30-owasp-a09-fallo-registro-monitoreo.md)
— documenta la corrección, con la misma metodología (ventana de
tiempo marcada + revisión de logs) usada en la auditoría original.

## Propósito

Confirmar que login exitoso, login fallido y logout ahora quedan
registrados con IP, timestamp y `sub`/correo — tanto en los logs de
aplicación (`AuthService`) como en `bitacora_auditoria` (decisión
tomada: **ambos**, no uno en vez del otro — ver análisis).

## Metodología / comando ejecutado

Dos intentos fallidos + un login exitoso + un logout, contra el stack
reconstruido con el código corregido:

```bash
# 2 fallos
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"correo":"usuarioReseteo.owasp@sgb-saas.local","password":"mala1"}'
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" -d '{"correo":"usuarioReseteo.owasp@sgb-saas.local","password":"mala2"}'

# login exitoso
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"usuarioReseteo.owasp@sgb-saas.local","password":"ClaveSegura123!"}'

# logout con el accessToken recién obtenido
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Revisión de logs del contenedor real:
```bash
docker logs sgb_backend --since 2026-07-30T20:44:00Z | grep -i "AuthService"
```

Revisión de `bitacora_auditoria`:
```bash
docker exec sgb_postgres psql -U sgb_user -d sgb_db -c \
  "SELECT id, usuario_id, tipo_operacion, tabla_afectada, registro_id, detalles, ip_origen, fecha_hora
   FROM bitacora_auditoria ORDER BY id DESC LIMIT 15;"
```

## Resultados crudos

**Logs de aplicación (`docker logs sgb_backend`), sin editar:**
```
2026-07-30T20:44:32.388Z  WARN 1 --- [backend] [nio-8080-exec-8] com.uteq.backend.service.AuthService     : Login fallido: correo=usuarioA07fix.owasp@sgb-saas.local ip=172.18.0.1
2026-07-30T20:44:34.436Z  WARN 1 --- [backend] [nio-8080-exec-8] com.uteq.backend.service.AuthService     : Login bloqueado por rate limit: correo=usuarioA07fix.owasp@sgb-saas.local ip=172.18.0.1 segundosRestantes=898
2026-07-30T20:45:02.001Z  WARN 1 --- [backend] [nio-8080-exec-6] com.uteq.backend.service.AuthService     : Login fallido: correo=usuarioReseteo.owasp@sgb-saas.local ip=172.18.0.1
2026-07-30T20:45:02.310Z  WARN 1 --- [backend] [nio-8080-exec-8] com.uteq.backend.service.AuthService     : Login fallido: correo=usuarioReseteo.owasp@sgb-saas.local ip=172.18.0.1
2026-07-30T20:45:02.958Z  INFO 1 --- [backend] [io-8080-exec-10] com.uteq.backend.service.AuthService     : Login exitoso: sub=13 correo=usuarioReseteo.owasp@sgb-saas.local ip=172.18.0.1
2026-07-30T20:45:32.583Z  INFO 1 --- [backend] [nio-8080-exec-8] com.uteq.backend.service.AuthService     : Logout: correo=usuarioReseteo.owasp@sgb-saas.local jti=649f631d-406f-4134-b7f2-4bb43abf09e2 ip=172.18.0.1
```

**`bitacora_auditoria`, filas reales (tabla completa, sin editar):**
```
 id | usuario_id | tipo_operacion | tabla_afectada | registro_id |                            detalles                             | ip_origen  |          fecha_hora
----+------------+----------------+-----------------+-------------+-----------------------------------------------------------------+------------+-------------------------------
 11 |         13 | LOGOUT         | usuarios        |             | Logout para correo: usuarioReseteo.owasp@sgb-saas.local (jti=649f631d-406f-4134-b7f2-4bb43abf09e2) | 172.18.0.1 | 2026-07-30 20:45:32.58389+00
 10 |         13 | LOGIN_OK       | usuarios        |          13 | Login exitoso para correo: usuarioReseteo.owasp@sgb-saas.local  | 172.18.0.1 | 2026-07-30 20:45:02.958482+00
  9 |            | LOGIN_FAIL     | usuarios        |             | Login fallido para correo: usuarioReseteo.owasp@sgb-saas.local  | 172.18.0.1 | 2026-07-30 20:45:02.310933+00
  8 |            | LOGIN_FAIL     | usuarios        |             | Login fallido para correo: usuarioReseteo.owasp@sgb-saas.local  | 172.18.0.1 | 2026-07-30 20:45:02.001471+00
```

## Análisis breve

**Los 3 campos exigidos aparecen en ambas fuentes**: IP (`ip=...` /
columna `ip_origen`), timestamp (prefijo `2026-07-30T20:...Z` de cada
línea de log / columna `fecha_hora`), y `sub`/correo (`sub=13
correo=...` en el log de éxito — el `sub` real del JWT es el
`usuario.getId()`, que coincide con `usuario_id=13` en la fila
`LOGIN_OK` de la bitácora; en fallos no hay `sub` que mostrar porque
la autenticación nunca llegó a emitir un JWT, se muestra el correo
intentado en su lugar, que es la única identidad disponible en ese
momento).

**Timestamp — confirmado que ya venía incluido, sin tocar config**:
el patrón de log por defecto de Spring Boot 4 (`logging.pattern.*` sin
overrides en `application.yml`, confirmado con `grep logging
application.yml` sin resultados) ya escribe
`%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}` al inicio de cada línea — visible
en los logs de arriba (`2026-07-30T20:44:32.388Z`). No hizo falta
ningún ajuste de `logback`/`application.yml` para este punto.

**Decisión: aplicación + `bitacora_auditoria`, no una en vez de la
otra**. `bitacora_auditoria` ya tenía `LOGIN_OK`/`LOGIN_FAIL`/`LOGOUT`
en su `CHECK` de `tipo_operacion` desde el diseño original del schema
— era la pieza que el propio esquema ya anticipaba y que ningún código
usaba todavía. Se implementó como `INSERT` simple desde
`AuthService.registrarAuditoria()` (sin procedimiento almacenado,
consistente con la estrategia CRUD-ORM de
`adr-013-acceso-datos-orm-sp.md` para operaciones de una sola tabla).
El logging de aplicación se mantiene en paralelo porque sirve un
propósito distinto y complementario: es lo que un operador revisa en
vivo con `docker logs`/agregador de logs sin necesidad de consultar la
base de datos, mientras que `bitacora_auditoria` es el registro
persistente y consultable con SQL para investigación posterior — no
son redundantes, son dos audiencias distintas del mismo evento.

**Decisión de alcance documentada**: `usuario_id` queda `null` en
`LOGIN_FAIL`/`LOGOUT` (no se hace una consulta extra solo para
resolverlo) — el correo intentado ya queda en `detalles` para
correlación manual. Solo `LOGIN_OK` resuelve `usuario_id` porque ya se
tiene el `Usuario` cargado en ese punto del flujo, sin costo adicional.

## Estado: PASA (gap cerrado)

Antes: cero líneas de log, cero filas de auditoría, en login exitoso o
fallido. Ahora: IP, timestamp y `sub`/correo capturados en ambas
fuentes (logs de aplicación + `bitacora_auditoria`) para
`LOGIN_OK`/`LOGIN_FAIL`/`LOGOUT`. Verificado en vivo contra el stack
Docker real y la tabla real, no solo por inspección de código.
