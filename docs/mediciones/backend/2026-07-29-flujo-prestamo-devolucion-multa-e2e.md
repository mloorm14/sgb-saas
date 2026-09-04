# Evidencia — Flujo E2E préstamo → devolución con atraso → multa → bloqueo → pago → desbloqueo

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-29T23:39:26Z
- **Commit**: `f33194c`
- **Docker**: Docker version 29.3.1, build c2be9cc
- **Docker Compose**: Docker Compose version v5.1.1
- **Java**: java version "21.0.10" 2026-01-20 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9
- **Herramienta**: Postman (colección `avance_3`, requests manuales, sin scripts de pre/post-request)

## Propósito

Evidencia cruda de la verificación manual end-to-end exigida en la
sección 7 de `docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md`: el
flujo completo de negocio (préstamo → devolución tardía → multa →
bloqueo del lector → pago → desbloqueo) probado por HTTP real contra el
stack Docker Compose local, no a nivel de servicio/repositorio (esa capa
ya está cubierta por `PrestamoServiceTest`, `MultaServiceTest` y
`PrestamoMultaProcedureIntegrationTest`).

## Metodología / comando ejecutado

Secuencia ejecutada manualmente en Postman (colección `avance_3`) contra
`http://localhost:8080`, intercalada con consultas SQL directas vía
`docker exec -it sgb_postgres psql -U sgb_user -d sgb_db` para resolver
el id del usuario lector (no expuesto por ningún endpoint GET del módulo)
y para simular el atraso de la fecha de devolución estimada.

1. `POST /api/auth/login` — `biblio@correo.com` → obtener `accessToken`.
2. `SELECT u.id, u.correo FROM usuarios u JOIN usuario_roles ur ON ur.usuario_id = u.id JOIN roles r ON r.id = ur.rol_id WHERE r.nombre = 'LECTOR';` → id del lector usado = `10`.
3. `POST /api/v1/prestamos` — Bearer biblio, body `{"usuarioId":10,"libroId":1,"diasPrestamo":7}`.
4. `UPDATE prestamos SET fecha_devolucion_estimada = NOW() - INTERVAL '1 day' WHERE id = 9;` (simulación de atraso sobre el préstamo creado en el paso 3, id 9).
5. `POST /api/v1/prestamos/9/devolucion` — Bearer biblio.
6. `POST /api/auth/login` — `lector@correo.com` (verificación de bloqueo).
7. `GET /api/v1/multas/usuario/10` — Bearer biblio.
8. `POST /api/v1/multas/6/pago` — Bearer biblio.
9. `POST /api/auth/login` — `lector@correo.com` (verificación de desbloqueo).

## Resultados crudos

### 1. Crear préstamo

Request: `POST http://localhost:8080/api/v1/prestamos`
```json
{
  "usuarioId": 10,
  "libroId": 1,
  "diasPrestamo": 7
}
```

Response: `201 Created` · 50 ms · 681 B
```json
{
    "id": 9,
    "usuarioId": 10,
    "libroId": 1,
    "bibliotecarioId": 9,
    "reservacionId": null,
    "fechaPrestamo": "2026-07-29T23:26:29.506454Z",
    "fechaDevolucionEstimada": "2026-08-05T23:26:29.506454Z",
    "fechaDevolucionReal": null,
    "renovacionesRealizadas": 0,
    "estadoPrestamoId": 1
}
```

### 2. Registrar devolución (préstamo con atraso simulado)

Request: `POST http://localhost:8080/api/v1/prestamos/9/devolucion`

Response: `200 OK` · 49 ms · 474 B
```json
{
    "prestamoId": 9,
    "huboMulta": true,
    "montoMulta": 1.00
}
```

### 3. Login lector — bloqueado

Request: `POST http://localhost:8080/api/auth/login`
```json
{
  "correo": "lector@correo.com",
  "password": "12345678"
}
```

Response: `423 Locked (WebDAV RFC 4918)` · 328 ms · 607 B
```json
{
    "detail": "Cuenta bloqueada por multas pendientes. Regularice su situación para continuar.",
    "instance": "/api/auth/login",
    "status": 423,
    "title": "Locked"
}
```

### 4. Ver multas del usuario

Request: `GET http://localhost:8080/api/v1/multas/usuario/10`

Response: `200 OK` · 72 ms · 881 B
```json
{
    "content": [
        {
            "id": 6,
            "prestamoId": 9,
            "monto": 1.00,
            "estadoMultaId": 1,
            "fechaGenerada": "2026-07-29T23:29:57.603621Z",
            "fechaPagada": null,
            "observaciones": null
        }
    ],
    "empty": false,
    "first": true,
    "last": true,
    "number": 0,
    "numberOfElements": 1,
    "pageable": {
        "offset": 0,
        "pageNumber": 0,
        "pageSize": 10,
        "paged": true,
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        }
    }
}
```

### 5. Pago de la multa

Request: `POST http://localhost:8080/api/v1/multas/6/pago`

Response: `200 OK` · 43 ms · 463 B
```json
{
    "multaId": 6,
    "usuarioDesbloqueado": true
}
```

### 6. Login lector — desbloqueado

Request: `POST http://localhost:8080/api/auth/login`
```json
{
  "correo": "lector@correo.com",
  "password": "12345678"
}
```

Response: `200 OK` · 363 ms · 1.13 KB
```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMCIsImNvbnJlbyI6ImxlY3RvckB1dGVxLmVkdS5lYyIsInJvbGVzIjpbIlJPTEVfTEVDVE9SIl0sImV4cCI6MTc4MzY5MTY2Nn0.[firma truncada en la captura]",
    "expiresIn": 3600,
    "tokenType": "Bearer"
}
```

## Análisis breve

Confirma de punta a punta, contra el stack real (Spring Boot + Postgres +
Redis en Docker, sin mocks), el circuito completo de negocio: creación de
préstamo, generación automática de multa al registrar una devolución
tardía (`huboMulta: true`, `montoMulta: 1.00`), bloqueo del usuario
moroso a nivel de autenticación (**423 Locked**, no un simple flag
interno ignorado por el login), y desbloqueo inmediato y verificado tras
`POST /api/v1/multas/{id}/pago` (`usuarioDesbloqueado: true` en la
respuesta, y el login subsiguiente vuelve a dar 200 con `accessToken`
nuevo). También valida indirectamente, invocados vía HTTP real y no solo
en el test de integración a nivel de repositorio, los métodos migrados a
`@Query(nativeQuery = true)` (fix documentado en
`docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md`).

**Limitaciones**: no cubre reservaciones ni el job `@Scheduled` de
expiración (`ReservacionScheduler`) — quedan fuera del alcance de esta
evidencia puntual. Tampoco cubre el caso de un `BIBLIOTECARIO` intentando
anular una multa fuera de su rol (`LB422`), ya cubierto por
`MultaServiceTest`. No se verificó el descuento de `stock_disponible` del
libro tras la creación del préstamo (no se capturó el `GET /api/v1/libros`
antes/después en esta corrida).