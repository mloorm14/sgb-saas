# Evidencia — Integración E2E del frontend (préstamos, reservaciones,
# multas) contra el backend real, con verificación de control de acceso
# por rol

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-30T18:46:00Z (aprox., ver timestamps
  individuales de cada respuesta más abajo)
- **Commit frontend** (`feature/prestamos-frontend`): `2f5d48e`
- **Commit backend** (`feature/prestamos-backend`): `20b46c1`
- **Commit de esta corrida** (rama temporal local
  `temp-integracion-e2e` = merge de las dos anteriores + fix de
  `.dockerignore`, **no pusheada, solo para generar esta evidencia**):
  `2a4b48f`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **PostgreSQL** (contenedor `sgb_postgres`): 16 (imagen pinada por
  digest en `docker-compose.yml`)
- **Herramienta**: PowerShell 5.1 (`Invoke-RestMethod`), llamando a los
  mismos endpoints y con las mismas query params que arman los 4
  componentes Angular nuevos (`PrestamosLectorComponent`,
  `PrestamosGestionComponent`, `ReservacionesComponent`,
  `MultasComponent`) — no se usó el navegador directamente porque el
  proyecto no tiene un runner E2E (Cypress/Playwright) configurado; esta
  evidencia reproduce exactamente las requests HTTP que esos componentes
  emiten (mismo verbo, misma ruta, mismos query params de paginación
  `page`/`size`/`sort`, mismo shape de body).

## Propósito

Hasta esta corrida, el frontend de Moisés Panamá (componentes de
préstamos/reservaciones/multas) solo se había verificado contra mocks
(`HttpClientTestingModule`) y contra la especificación en papel de
`docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md`. Esta evidencia
lo prueba contra el **backend real** de Cajas
(`feature/prestamos-backend`, ya con controllers/services/DTOs
implementados), confirmando:

1. Que los nombres de campo que usan los `.html` de los 4 componentes
   coinciden con los DTOs de respuesta reales del backend.
2. Que el contrato de request (`PrestamoRequestDTO`,
   `ReservacionRequestDTO`, `AnulacionMultaRequestDTO`) coincide con lo
   que arman los formularios — incluye la corrección del campo
   `diasPrestamo` (antes `dias`) hecha en el commit `2f5d48e`.
3. Que el control de acceso por rol que la UI usa para mostrar/ocultar
   botones (`AuthService.hasRole()`) es un espejo fiel de lo que el
   backend realmente exige con `@PreAuthorize` — no solo cosmético del
   lado del cliente.

## Preparación del entorno

```
git fetch origin
git checkout -b temp-integracion-e2e feature/prestamos-frontend
git merge origin/feature/prestamos-backend -m "merge temporal solo para pruebas E2E locales (no se sube)"
bash scripts/build-init-sql.sh
docker compose down -v
docker compose up -d --build
```

Salud de contenedores confirmada antes de empezar:
```
docker inspect --format='{{.State.Health.Status}}' sgb_backend
→ healthy
```

## Usuarios de prueba

No existía en `db/seed.sql` ningún usuario con rol LECTOR o
BIBLIOTECARIO (solo `admin@sgb-saas.local`, rol ADMIN), así que se
crearon dos cuentas nuevas vía `POST /api/auth/registro` (rol LECTOR
por defecto) y se promovió una a BIBLIOTECARIO directamente por SQL
(no existe endpoint para asignar roles).

| Usuario | id | Rol(es) final(es) | Uso en esta evidencia |
|---|---|---|---|
| `lector.e2e@correo.com` | 2 | LECTOR | Simula al lector real usando `PrestamosLectorComponent`, `ReservacionesComponent` y `MultasComponent` |
| `biblio.e2e@correo.com` | 3 | BIBLIOTECARIO | Simula al bibliotecario usando `PrestamosGestionComponent` y `MultasComponent` (acción Pagar) |
| `admin@sgb-saas.local` | (seed) | ADMIN | Simula GERENTE/ADMIN usando `MultasComponent` (acción Anular) |

### Hallazgo durante la preparación (documentado porque es información
### real sobre el sistema, no un defecto del frontend)

El primer intento de promover a `biblio.e2e` fue **agregar** el rol
BIBLIOTECARIO sin quitarle LECTOR. Con ambos roles, `GET
/api/v1/prestamos/usuario/{id}` devolvió `403 Forbidden` al pedir los
préstamos de otro usuario. Causa (confirmada leyendo
`PrestamoService.validarAccesoUsuario`): el método trata como LECTOR a
cualquier cuenta que tenga **esa** authority entre sus roles, sin
importar si también tiene BIBLIOTECARIO, y en ese caso exige que el
`usuarioId` solicitado sea el propio. Se corrigió quitándole el rol
LECTOR a `biblio.e2e` por SQL (`DELETE FROM usuario_roles ...`) — no es
un bug de nadie, es el comportamiento esperado del sistema para una
cuenta multi-rol, y quedó registrado acá para que el equipo lo tenga
presente si en el futuro se decide que un mismo usuario pueda tener
más de un rol operativo simultáneo.

## Metodología / secuencia ejecutada

1. `POST /api/auth/registro` × 2 (lector.e2e, biblio.e2e).
2. `INSERT`/`DELETE` en `usuario_roles` vía `docker exec psql` para dejar
   a biblio.e2e solo con BIBLIOTECARIO.
3. `POST /api/auth/login` × 3 (lector.e2e, biblio.e2e, admin).
4. `POST /api/v1/prestamos` (bibliotecario crea préstamo #1 para el
   lector) — verifica el fix de `diasPrestamo`.
5. `GET /api/v1/prestamos/usuario/2?page=0&size=10&sort=id,desc`
   (bibliotecario) — mismos query params que arma
   `PrestamosGestionComponent.cargarPagina()`.
6. `POST /api/v1/prestamos/1/devolucion` (bibliotecario) — sin atraso,
   `huboMulta: false`.
7. `POST /api/v1/reservaciones` (lector, reserva para sí mismo) —
   mismo shape de body que arma `ReservacionesComponent` cuando
   `esLector = true`.
8. `GET /api/v1/reservaciones/usuario/2?page=0&size=10&sort=id,desc`
   (lector).
9. `POST /api/v1/prestamos` (préstamo #2) + `UPDATE` SQL para simular
   atraso + `POST /api/v1/prestamos/2/devolucion` → genera multa #1
   (`huboMulta: true`, `montoMulta: 1.00`).
10. `GET /api/v1/multas/usuario/2?page=0&size=10&sort=id,desc` (lector)
    — mismos query params que arma `MultasComponent.cargarPagina()`.
11. `POST /api/v1/multas/1/pago` como **lector** → **403 esperado**
    (confirma por qué `MultasComponent` no le muestra el botón "Pagar"
    a un LECTOR).
12. `POST /api/v1/multas/1/anulacion` como **bibliotecario** → **403
    esperado** (confirma por qué `MultasComponent` solo muestra
    "Anular" a GERENTE/ADMIN, no a BIBLIOTECARIO).
13. `POST /api/v1/multas/1/pago` como bibliotecario → éxito,
    `usuarioDesbloqueado: true`.
14. Préstamo #3 + atraso simulado + devolución → genera multa #2.
15. `POST /api/v1/multas/2/anulacion` con `motivo` obligatorio, como
    **admin** (rol ADMIN, satisface `GERENTE/ADMIN`) → éxito.

## Resultados crudos

### 1. Registro de usuarios de prueba

`POST /api/auth/registro` (lector.e2e) y (biblio.e2e) → `201 Created`
en ambos casos. Confirmado por consulta posterior:

```
 id | nombre | correo                  | roles
----+--------+-------------------------+---------
  2 | Lector | lector.e2e@correo.com  | {LECTOR}
  3 | Biblio | biblio.e2e@correo.com  | {LECTOR}
```

Tras promover/corregir roles por SQL:

```
         correo          |     nombre
--------------------------+---------------
 biblio.e2e@correo.com  | BIBLIOTECARIO
 lector.e2e@correo.com  | LECTOR
```

### 2. Crear préstamo — verificación del fix `dias` → `diasPrestamo`

Request: `POST http://localhost:8080/api/v1/prestamos`
```json
{ "usuarioId": 2, "libroId": 1, "diasPrestamo": 5 }
```

Response: `201 Created`
```json
{
    "id": 1,
    "usuarioId": 2,
    "libroId": 1,
    "bibliotecarioId": 3,
    "reservacionId": null,
    "fechaPrestamo": "2026-07-30T18:46:30.808071Z",
    "fechaDevolucionEstimada": "2026-08-04T18:46:30.808071Z",
    "fechaDevolucionReal": null,
    "renovacionesRealizadas": 0,
    "estadoPrestamoId": 1
}
```
`fechaDevolucionEstimada` = `fechaPrestamo` + 5 días exactos → confirma
que `diasPrestamo` llegó correctamente al backend (con el nombre de
campo viejo, `dias`, este valor hubiera sido `null`/rechazado por
`@NotNull` en `PrestamoRequestDTO`).

### 3. Listar préstamos por usuario (paginado, como lo llama la UI)

Request: `GET
http://localhost:8080/api/v1/prestamos/usuario/2?page=0&size=10&sort=id,desc`

Response: `200 OK`
```json
{
    "content": [
        {
            "id": 1, "usuarioId": 2, "libroId": 1, "bibliotecarioId": 3,
            "reservacionId": null,
            "fechaPrestamo": "2026-07-30T18:46:30.808071Z",
            "fechaDevolucionEstimada": "2026-08-04T18:46:30.808071Z",
            "fechaDevolucionReal": null,
            "renovacionesRealizadas": 0, "estadoPrestamoId": 1
        }
    ],
    "totalElements": 1, "totalPages": 1, "number": 0, "size": 10,
    "first": true, "last": true, "empty": false
}
```
Confirma que `data.content` y `data.totalPages` — los dos campos que
leen `PrestamosLectorComponent.cargarPrestamos()` y
`PrestamosGestionComponent.cargarPagina()` — existen tal cual en la
respuesta real.

### 4. Registrar devolución (sin atraso)

Request: `POST http://localhost:8080/api/v1/prestamos/1/devolucion`

Response: `200 OK`
```json
{ "prestamoId": 1, "huboMulta": false, "montoMulta": null }
```

### 5. Crear reservación (lector, para sí mismo)

Request: `POST http://localhost:8080/api/v1/reservaciones`
```json
{ "usuarioId": 2, "libroId": 2 }
```

Response: `201 Created`
```json
{
    "id": 1, "usuarioId": 2, "libroId": 2, "estadoReservacionId": 1,
    "fechaReserva": "2026-07-30T18:48:58.943509586Z",
    "fechaLimiteRetiro": "2026-07-31T18:48:58.943509586Z"
}
```

### 6. Listar reservaciones propias (paginado)

Request: `GET
http://localhost:8080/api/v1/reservaciones/usuario/2?page=0&size=10&sort=id,desc`

Response: `200 OK` · `totalElements: 1`, `totalPages: 1`, `content[0].id: 1`
(mismo campo que lee `ReservacionesComponent.cargarPagina()`).

### 7. Generar multa por atraso (préstamo #2)

Request: `POST http://localhost:8080/api/v1/prestamos`
```json
{ "usuarioId": 2, "libroId": 3, "diasPrestamo": 5 }
```
→ `id: 2`

`UPDATE prestamos SET fecha_devolucion_estimada = NOW() - INTERVAL '1
day' WHERE id = 2;` (simulación de atraso, mismo mecanismo usado en la
evidencia de Cajas).

Request: `POST http://localhost:8080/api/v1/prestamos/2/devolucion`

Response: `200 OK`
```json
{ "prestamoId": 2, "huboMulta": true, "montoMulta": 1.00 }
```

### 8. Listar multas propias (lector, paginado)

Request: `GET
http://localhost:8080/api/v1/multas/usuario/2?page=0&size=10&sort=id,desc`

Response: `200 OK`
```json
{
    "content": [
        {
            "id": 1, "prestamoId": 2, "monto": 1.00, "estadoMultaId": 1,
            "fechaGenerada": "2026-07-30T18:49:59.215039Z",
            "fechaPagada": null, "observaciones": null
        }
    ],
    "totalElements": 1, "totalPages": 1
}
```

### 9. Control de acceso — lector intenta pagar su propia multa

Request: `POST http://localhost:8080/api/v1/multas/1/pago`
(Bearer: `lector.e2e`)

Response: **`403 Forbidden`**

Confirma por qué `MultasComponent` calcula `puedeGestionar =
hasRole('BIBLIOTECARIO','GERENTE','ADMIN')` y no le muestra el botón
"Pagar" a un LECTOR — el backend lo hubiera rechazado igual.

### 10. Control de acceso — bibliotecario intenta anular una multa

Request: `POST http://localhost:8080/api/v1/multas/1/anulacion`
```json
{ "motivo": "Prueba de permisos E2E" }
```
(Bearer: `biblio.e2e`, rol BIBLIOTECARIO)

Response: **`403 Forbidden`**

Confirma por qué `MultasComponent` calcula `puedeAnular =
hasRole('GERENTE','ADMIN')` (más restrictivo que `puedeGestionar`) y
solo le muestra "Anular" a GERENTE/ADMIN, nunca a BIBLIOTECARIO — el
backend lo hubiera rechazado igual.

### 11. Pago exitoso (bibliotecario, rol correcto)

Request: `POST http://localhost:8080/api/v1/multas/1/pago`
(Bearer: `biblio.e2e`)

Response: `200 OK`
```json
{ "multaId": 1, "usuarioDesbloqueado": true }
```

### 12. Segunda multa (préstamo #3 con atraso) y anulación exitosa

`POST /api/v1/prestamos` → `id: 3` → `UPDATE` fecha estimada → `POST
/api/v1/prestamos/3/devolucion` → `{ "huboMulta": true, "montoMulta":
1.00 }` → multa generada con `id: 2`.

Request: `POST http://localhost:8080/api/v1/multas/2/anulacion`
```json
{ "motivo": "Prueba E2E: verificacion del flujo de anulacion con motivo obligatorio" }
```
(Bearer: `admin@sgb-saas.local`, rol ADMIN)

Response: `200 OK`
```json
{ "multaId": 2, "usuarioDesbloqueado": true }
```

Confirma el flujo completo del modal de `MultasComponent`
(`abrirModalAnular` → `motivo` obligatorio → `confirmarAnulacion` →
`POST /{id}/anulacion`), incluida la exigencia de rol GERENTE/ADMIN.

## Conclusión

Los 4 componentes nuevos (`PrestamosLectorComponent`,
`PrestamosGestionComponent`, `ReservacionesComponent`,
`MultasComponent`) fueron verificados end-to-end contra el backend real
de Cajas: mismos endpoints, mismos verbos HTTP, mismos nombres de campo
de request y de response, misma paginación, y mismo control de acceso
por rol (los 2 casos `403` reproducen exactamente las condiciones bajo
las que la UI oculta los botones correspondientes). No se encontró
ningún mismatch adicional a los ya corregidos en los commits
`2f5d48e` (campo `diasPrestamo`) y anteriores (matchers de paginación en
los `.spec.ts`).
