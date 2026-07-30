# Evidencia — OWASP A01:2021 Control de acceso roto (Bloque C.2)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T19:23:24Z
- **Commit**: `162bf3a`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Propósito

Bloque C.2 de la guía de la Tercera Entrega: verificar que un usuario
autenticado con rol LECTOR no pueda leer datos de otro usuario a través
de un endpoint que expone un `usuarioId` en la ruta
(`GET /api/v1/prestamos/usuario/{usuarioId}`). Control relacionado:
`PrestamoService.validarAccesoUsuario()`
(`backend-springboot/src/main/java/com/uteq/backend/service/PrestamoService.java`).

## Metodología / comando ejecutado

Dos usuarios LECTOR nuevos, creados vía `/api/auth/registro` contra el
stack Docker real (`docker compose up -d --build`, sin volumen limpio):

```bash
curl -s -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Ana","apellido":"Auditoria","correo":"usuarioA.owasp@sgb-saas.local","password":"ClaveSegura123!"}'
curl -s -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Beto","apellido":"Auditoria","correo":"usuarioB.owasp@sgb-saas.local","password":"ClaveSegura123!"}'
```

Login como Usuario A, y dos llamadas: una pidiendo los préstamos de
Usuario B (cruzada, debe fallar) y otra pidiendo los suyos propios
(control, debe funcionar):

```bash
LOGIN_A=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"usuarioA.owasp@sgb-saas.local","password":"ClaveSegura123!"}')
TOKEN_A=$(echo "$LOGIN_A" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

curl --include -s -X GET "http://localhost:8080/api/v1/prestamos/usuario/4" \
  -H "Authorization: Bearer $TOKEN_A"

curl --include -s -X GET "http://localhost:8080/api/v1/prestamos/usuario/3" \
  -H "Authorization: Bearer $TOKEN_A"
```

Usuario A = id `3`, Usuario B = id `4` (confirmado en la respuesta de
`/api/auth/registro` de cada uno, ver más abajo).

## Resultados crudos

**Registro de los 2 usuarios:**
```
{"id":3,"nombre":"Ana","correo":"usuarioA.owasp@sgb-saas.local","roles":["LECTOR"]}
{"id":4,"nombre":"Beto","correo":"usuarioB.owasp@sgb-saas.local","roles":["LECTOR"]}
```

**Caso 1 — A intenta leer los préstamos de B (`GET /api/v1/prestamos/usuario/4`), sin editar:**
```
HTTP/1.1 403
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
Date: Thu, 30 Jul 2026 19:23:16 GMT

{"detail":"No tiene permisos para realizar esta acción.","instance":"/api/v1/prestamos/usuario/4","status":403,"title":"Forbidden"}
```

**Caso 2 (control) — A lee sus propios préstamos (`GET /api/v1/prestamos/usuario/3`), sin editar:**
```
HTTP/1.1 200
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Thu, 30 Jul 2026 19:23:16 GMT

{"content":[],"empty":true,"first":true,"last":true,"number":0,"numberOfElements":0,"pageable":{"offset":0,"pageNumber":0,"pageSize":10,"paged":true,"sort":{"empty":false,"sorted":true,"unsorted":false},"unpaged":false},"size":10,"sort":{"empty":false,"sorted":true,"unsorted":false},"totalElements":0,"totalPages":0}
```

## Análisis breve

El acceso cruzado devuelve **403 Forbidden** con `ProblemDetail` (RFC
7807), no 200 con datos ajenos ni un 500 que sugiera un bug — es el
resultado correcto y esperado. El control confirma que no es un bloqueo
general mal configurado (A ve su propia lista, vacía porque el usuario
de prueba no tiene préstamos, pero con 200, no 403).

El mecanismo real: `PrestamoService.validarAccesoUsuario()` compara el
`usuarioId` de la ruta contra el id resuelto del `correo` del JWT
(`Authentication.getName()`) **solo cuando el rol es LECTOR** — roles
BIBLIOTECARIO/GERENTE no tienen esta restricción (correcto, por diseño:
el personal opera sobre cualquier usuario). No se probó aquí el caso
BIBLIOTECARIO/GERENTE porque no es el escenario de riesgo de A01 (un
LECTOR escalando a datos de otro LECTOR) — el caso de "personal
autorizado viendo cualquier usuario" es el comportamiento previsto, no
una falla de control de acceso.

**Limitación de esta evidencia**: solo se probó el endpoint de
préstamos. `MultaController`/`ReservacionController` implementan la
misma validación (`validarAccesoUsuario`, mismo patrón, ver
`docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md`), pero no se
repitió la prueba en cada uno — el mecanismo es idéntico y compartido,
no una implementación distinta por controller.

## Estado: PASA
