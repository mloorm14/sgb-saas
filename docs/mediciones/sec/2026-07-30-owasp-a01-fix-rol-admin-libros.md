# Evidencia — OWASP A01:2021 Control de acceso roto — FIX aplicado (rol ADMIN en LibroController)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T22:23:57Z
- **Commit**: `6c351cf` (backend reconstruido con el fix inmediatamente después)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9

## Referencia al gap original

Este archivo **no reemplaza ni edita**
[`2026-07-30-owasp-a01-control-acceso-roto.md`](2026-07-30-owasp-a01-control-acceso-roto.md)
— documenta la corrección de un hallazgo puntual detectado durante esa
auditoría (mencionado en su momento como nota, no como gap bloqueante):
`LibroController` no incluía el rol `ADMIN` en la lista de roles
permitidos de ninguno de sus 5 endpoints, pese a que el resto del
sistema modela `ADMIN` como el rol de mayor privilegio
(`JwtService.JERARQUIA_ROLES = ADMIN > GERENTE > BIBLIOTECARIO >
LECTOR`).

## Propósito

Confirmar que un usuario cuyo **único** rol es `ADMIN` (sin
`GERENTE`/`BIBLIOTECARIO`) ahora puede listar, ver, crear, editar y
eliminar (baja lógica) libros del catálogo — antes del fix, ese mismo
usuario recibía `403` en los 5 endpoints.

## Metodología / comandos ejecutados

Backend reconstruido con el fix:
```bash
docker compose up -d --build backend
```

Usuario de prueba con **solo** rol ADMIN (registro normal asigna
LECTOR por defecto; el rol se reemplaza directo en la base de datos
para reproducir exactamente el escenario del hallazgo):
```bash
curl -s -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" \
  -d '{"nombre":"Admin","apellido":"DeSistema","correo":"adminfix.owasp@sgb-saas.local","password":"ClaveSegura123!"}'

docker exec sgb_postgres psql -U sgb_user -d sgb_db -c "
DELETE FROM usuario_roles WHERE usuario_id = 20;
INSERT INTO usuario_roles (usuario_id, rol_id) SELECT 20, id FROM roles WHERE nombre = 'ADMIN';
"
```

Login y prueba de los 5 endpoints:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"adminfix.owasp@sgb-saas.local","password":"ClaveSegura123!"}' | jq -r .accessToken)

curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/libros -H "Authorization: Bearer $TOKEN"

curl -s -w "\n%{http_code}\n" -X POST http://localhost:8080/api/v1/libros -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"titulo":"Libro de Prueba ADMIN Fix","isbn":"9999999999999","anioPublicacion":2024,"resumen":"prueba","editorialId":1,"idiomaId":1,"estadoId":1,"stockTotal":1,"stockDisponible":1}'

curl -s -w "\n%{http_code}\n" -X PUT http://localhost:8080/api/v1/libros/18 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"titulo":"Libro de Prueba ADMIN Fix (editado)", ...}'

curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8080/api/v1/libros/18 -H "Authorization: Bearer $TOKEN"
```

Regresión — confirmar que el fix no sobre-otorgó permisos a un rol sin
relación con el catálogo:
```bash
LECTOR_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"usuarioReseteo.owasp@sgb-saas.local","password":"ClaveSegura123!"}' | jq -r .accessToken)
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/libros -H "Authorization: Bearer $LECTOR_TOKEN" -H "Content-Type: application/json" \
  -d '{"titulo":"No deberia crear esto", ...}'
```

## Resultados crudos

| Endpoint | Antes del fix (usuario solo-ADMIN) | Después del fix |
|---|---|---|
| `GET /api/v1/libros` | 403 | **200** |
| `POST /api/v1/libros` | 403 | **201** |
| `PUT /api/v1/libros/{id}` | 403 | **200** |
| `DELETE /api/v1/libros/{id}` | 403 | **204** |
| `POST /api/v1/libros` con rol `LECTOR` (regresión) | 403 | **403 (sin cambios, correcto)** |

Body real de la creación (`201`):
```json
{"id":18,"titulo":"Libro de Prueba ADMIN Fix","isbn":"9999999999999","resumen":"prueba","portadaUrl":null,"anioPublicacion":2024,"editorialId":1,"editorial":"Prentice Hall","idiomaId":1,"idioma":"Español","estadoId":1,"estado":"ACTIVO","stockTotal":1,"stockDisponible":1,"ubicacionFisica":null,"fechaRegistro":"2026-07-30T22:23:29.597163654Z"}
```

## Análisis breve

El fix agrega `ADMIN` a los 5 `@PreAuthorize` de `LibroController`
(mismo criterio que ya tenían `GERENTE`/`BIBLIOTECARIO`, ya que
`ADMIN` es el rol de mayor privilegio en la jerarquía establecida en
`JwtService.JERARQUIA_ROLES`). Verificado en vivo con un usuario cuyo
**único** rol es `ADMIN` — no alcanza con tener `ADMIN` junto a otro
rol que ya tuviera acceso, porque eso habría enmascarado el gap real.
La regresión con `LECTOR` confirma que el fix es aditivo (agrega
exactamente el rol pedido) y no relaja el resto del control de acceso.

**Nota de alcance**: `PrestamoController` y `ReservacionController`
tienen el mismo patrón (no incluyen `ADMIN` en ningún
`@PreAuthorize`) — no se tocaron en este fix porque no fue lo pedido,
pero queda documentado acá para que quede visible si en algún momento
se decide una política uniforme de `ADMIN` en todo el sistema.

## Estado: PASA (gap cerrado en LibroController)

Antes: un usuario exclusivamente `ADMIN` recibía `403` en los 5
endpoints del catálogo. Ahora: los 5 endpoints responden como los de
`GERENTE`. Verificado en vivo contra el stack Docker real, con un
usuario cuyo único rol es `ADMIN`, no por inspección de código.
