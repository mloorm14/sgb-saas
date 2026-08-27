# Instrucciones — Frontend de Préstamos/Reservaciones/Multas + fixes pendientes

Para: **Moises Panama**. Autocontenido — todo lo que necesitás saber está
acá o referenciado con ruta exacta. Verificado contra el código real del
repositorio (y, en el caso del healthcheck, contra un contenedor real
construido y corrido) antes de escribirse.

## 0. Punto de partida — qué YA existe

- Angular 17, **standalone components** (sin `NgModule`), Angular
  standalone routing con `@angular/router`.
- Componente de referencia completo:
  `frontend-angular/src/app/libros/libros.component.ts` +
  `.html` — CRUD con paginación manual (`page`/`size` como query params,
  `currentPage`/`pageSize`/`totalPages` en el componente, sin librería de
  paginación de terceros).
- `core/services/auth.service.ts` — `accessToken` en memoria (variable
  privada de la clase, nunca `localStorage`), `login()`/`registro()`/
  `logout()`/`getAccessToken()`/`isLoggedIn()`.
- `core/interceptors/jwt.interceptor.ts` — adjunta `Authorization: Bearer
  <token>` a cada request saliente; en un 401 fuera de `/auth/`, desloguea
  y redirige a `/login`.
- `core/guards/auth.guard.ts` — `CanActivateFn` simple basado en
  `authService.isLoggedIn()`.
- El backend de Cajas (préstamos/reservaciones/multas) puede no estar
  terminado todavía cuando arranques — no lo esperes. Construí contra la
  especificación de endpoints de
  `docs/reparto-entrega-3/cajas-backend/INSTRUCCIONES.md` (sección
  "Controllers REST", tabla de rutas/roles) y, si necesitás probar la UI
  antes de que el backend real responda, usá un mock temporal (ej.
  `HttpClientTestingModule` en un test, o un servicio con datos hardcoded
  detrás de la misma interfaz que después apunta al endpoint real) — no
  bloquees tu trabajo esperando a Cajas.

## 1. Componentes a construir

Mismo patrón que `LibrosComponent`: standalone, `ReactiveFormsModule`
donde haya formularios, paginación manual `page`/`size`, inyección de
`HttpClient`/`AuthService`/`Router` por constructor, manejo de error con
un `errorMsg: string` mostrado en el template (no hay librería de toasts
ni notificaciones en el proyecto, no introduzcas una nueva sin
necesidad).

- **`PrestamosLectorComponent`** (vista LECTOR): lista paginada de los
  préstamos propios (`GET /api/v1/prestamos/usuario/{miId}`, el backend
  ya resuelve "propio" contra el JWT — vos solo necesitás el id del
  usuario logueado; si `AuthService` no expone hoy el id del usuario
  además del token, es una ampliación pequeña y razonable que podés
  hacer vos mismo en `auth.service.ts`, decodificando el payload del JWT
  o agregando un endpoint `/api/auth/me` — evaluá con Cajas/Marlon cuál
  prefieren antes de implementarlo, no asumas una de las dos por tu
  cuenta si afecta al backend).
- **`PrestamosGestionComponent`** (vista BIBLIOTECARIO/GERENTE):
  formulario para crear préstamo (usuario + libro + días), listado con
  acción "Registrar devolución" por fila.
- **`ReservacionesComponent`**: crear reservación (LECTOR reserva para
  sí mismo), listado paginado propio/por usuario según rol.
- **`MultasComponent`**: LECTOR ve las propias (`GET
  /api/v1/multas/usuario/{miId}`); BIBLIOTECARIO/GERENTE ven listado con
  acción "Pagar"; GERENTE/ADMIN además ven acción "Anular" (con campo de
  motivo obligatorio en un formulario/modal antes de confirmar — el
  backend rechaza con 422 si el rol no es GERENTE/ADMIN, pero la UI no
  debería ni mostrar el botón a quien no tiene el rol, mismo criterio que
  ya usás implícitamente al mostrar/ocultar acciones de "Editar/Eliminar"
  en `LibrosComponent` — che, revisá si ese componente ya oculta botones
  por rol; si no lo hace, no hay ejemplo previo exacto y tenés que
  decidir cómo leer el rol actual en el template, ej. exponiendo un
  `getRoles()`/`hasRole()` en `AuthService` a partir del JWT decodificado).

Rutas nuevas en `app.routes.ts` (ver el patrón ya usado ahí para
`libros`), protegidas con `authGuard` igual que la ruta de libros.

## 2. Fix del healthcheck de `sgb_frontend` — causa raíz ya diagnosticada

**No lo arregles a ciegas ni copiés lo que hizo un intento anterior sin
entender por qué funcionó.** Se investigó la causa raíz de forma
empírica (build real de la imagen + contenedor real corriendo) antes de
escribir esto:

### Diagnóstico

`docker-compose.yml` define:
```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:80"]
```

Al construir la imagen real (`frontend-angular/Dockerfile`, basada en
`nginx@sha256:...` = `nginx:1.25-alpine`) y ejecutar exactamente ese
comando dentro del contenedor:

```
wget: can't connect to remote host: Connection refused
```

Pero contra `127.0.0.1` explícito, el mismo `wget` funciona
(`WGET_IPV4_OK`). La causa: dentro del contenedor, `localhost` resuelve
primero a `::1` (IPv6) —confirmado con `getent hosts localhost` → `::1
localhost localhost`—, pero `frontend-angular/nginx.conf` solo tiene
`listen 80;` (**sin** `listen [::]:80;`), así que nginx **no escucha en
IPv6**. Confirmado también con `ss -tlnp` dentro del contenedor: un solo
listener, `0.0.0.0:80`, ninguno en `:::80`. `wget` (BusyBox) intenta
`::1:80`, recibe "connection refused", y **no reintenta automáticamente
por IPv4** — a diferencia de `curl`, que sí hace ese fallback
automático (confirmado: `curl http://localhost:80` dentro del mismo
contenedor prueba IPv6, falla, reintenta IPv4, y responde `200 OK`).

Es decir: **el problema no es que falte `wget` ni que falte `curl`**
(ambos binarios existen en la imagen, verificado con `which wget; which
curl` — los dos devuelven ruta) — es específicamente que `wget` no hace
fallback de IPv6 a IPv4 y `nginx.conf` no escucha en IPv6.

### Dos formas válidas de arreglarlo (elegí una, no las dos)

1. **Apuntar el healthcheck a `127.0.0.1` en vez de `localhost`** (cambio
   de una palabra en `docker-compose.yml`, no toca `nginx.conf`):
   ```yaml
   test: ["CMD", "wget", "-q", "--spider", "http://127.0.0.1:80"]
   ```
2. **Hacer que nginx escuche también en IPv6** (agregar
   `listen [::]:80;` junto a `listen 80;` en
   `frontend-angular/nginx.conf`) — deja `localhost` funcionando tal
   cual está en `docker-compose.yml`, y de paso resuelve el mismo
   problema para cualquier otro cliente dentro del contenedor que
   resuelva `localhost` a IPv6 primero.

Cualquiera de las dos es correcta; la opción 2 es más robusta a largo
plazo (arregla la causa, no solo el síntoma del healthcheck), pero la 1
es más simple si preferís no tocar la config de nginx. **No cambies
`wget` por `curl` sin la opción 1 o 2** — cambiar solo el binario
"funciona" (porque `curl` sí hace fallback), pero deja el problema real
(nginx no escucha en IPv6) sin resolver y sin documentar, sorprendería a
cualquiera que dependa de IPv6 más adelante.

Verificá tu fix reconstruyendo la imagen y corriendo el healthcheck real
(`docker compose up -d --build frontend` y `docker inspect --format=
'{{json .State.Health}}' sgb_frontend` hasta ver `"Status":"healthy"`),
no solo revisando que el YAML "se ve bien".

## 3. Migración pendiente de `refreshToken` a cookie — coordinación explícita

`docs/adr/adr-012-cookies-jwt.md` **ya está implementado del lado del
backend**: el `refreshToken` viaja en una cookie
`HttpOnly+Secure+SameSite=Strict` con `path=/api/auth` desde
`/api/auth/login` y `/api/auth/refresh`, y **ya no viene en el cuerpo
JSON de la respuesta** (`TokenResponseDTO.refreshToken` tiene
`@JsonIgnore`). **El frontend actual no la gestiona en absoluto** —
`auth.service.ts` de hoy ni siquiera guarda un `refreshToken` (nunca lo
recibió del body, y ahora definitivamente no lo va a recibir ahí). Leé
el ADR completo antes de tocar nada — documenta por qué el
`accessToken` **NO se migra** (se queda en body/memoria tal cual está,
gestionado por `jwt.interceptor.ts` exactamente como hoy) y por qué la
migración del `refreshToken` es la única parte que te toca a vos ahora.

### Lo que tenés que ajustar

1. **CORS ya está listo para esto** — verificado en
   `backend-springboot/.../config/SecurityConfig.java`:
   `configuration.setAllowCredentials(true)` y el origen permitido es
   exactamente `http://localhost:4200`. Del lado Angular, cualquier
   `HttpClient` request hacia `/api/auth/*` necesita
   `withCredentials: true` para que el navegador adjunte/acepte la
   cookie — hoy ningún request de `auth.service.ts` lo tiene. Agregalo
   a las llamadas de `login`, `refresh` (nueva, ver punto 2) y `logout`.
2. **No existe hoy ninguna lógica de refresh automático** (confirmado en
   el propio ADR: "no hay lógica de refresh automático implementada
   todavía. en un 401 fuera de /auth/, el interceptor simplemente cierra
   sesión"). Tu trabajo: en `jwt.interceptor.ts`, antes de desloguear en
   un 401 (fuera de `/auth/`), intentá primero `POST
   /api/auth/refresh` con `withCredentials: true` (sin body — el
   `refreshToken` va en la cookie, el navegador la adjunta solo). Si
   ese refresh responde 200, actualizá el `accessToken` en memoria
   (`AuthService`) y reintentá la request original una vez; si el
   refresh también falla (400/401), ahí sí desloguear y redirigir, como
   hoy.
3. **El `accessToken` se queda exactamente como está** — sigue viniendo
   en el body de `/login`/`/refresh`, sigue guardado solo en memoria en
   `AuthService`, sigue viajando como header `Authorization: Bearer` vía
   `jwt.interceptor.ts`. No lo muevas a cookie — el ADR es explícito en
   que esa migración es un cambio de mayor alcance, deliberadamente
   pospuesto, que no es parte de esta tarea.

Si tenés dudas sobre el orden exacto de "refrescar antes de desloguear"
(hay más de una forma razonable de encadenar el `Observable` del
retry en RxJS), es un buen punto para confirmar con Marlon antes de
sobre-diseñarlo — no hay un patrón previo en este proyecto de "retry con
refresh" que puedas copiar tal cual.

## 4. Tests

`ng test` ya está configurado (`@angular-devkit/build-angular:karma`,
Jasmine). **Hoy solo existe un test real**:
`frontend-angular/src/app/app.component.spec.ts` (el que genera Angular
CLI por defecto) — ningún componente de negocio (`LibrosComponent`,
`AuthService`, etc.) tiene test todavía, así que no hay un ejemplo
interno de "cómo testear un componente que llama HttpClient" para
copiar 1:1. Usá `HttpClientTestingModule`/`provideHttpClientTesting()`
(patrón estándar de Angular, no específico de este proyecto) para mockear
las respuestas del backend en los componentes nuevos que creés. Cobertura
mínima razonable por componente: que cargue el listado inicial
correctamente, y que un error de HTTP muestre `errorMsg` sin romper la
UI.

## 5. Git

- Rama: `feature/prestamos-frontend`, desde `main` actualizado
  (`git pull origin main` antes de ramificar).
- Conventional Commits (`feat(frontend): ...`, `fix(frontend): ...`),
  igual que el resto del historial del proyecto.
- **PR hacia `main` cuando esté listo — nunca push directo a `main`**
  (solo Marlon tiene bypass del branch protection).

## 6. Tu parte de A.3 — `docs/requisitos/`

Mismo formato exigido que la parte de Cajas (`docs/requisitos/`, carpeta
que no existe todavía): historia de usuario **Connextra** + criterios
**Gherkin**, caso de uso **Cockburn** — pero desde la perspectiva de
UI/UX de quien usa la interfaz, no de la lógica de negocio del backend
(Cajas ya cubre esa parte con sus propios documentos; los tuyos son
complementarios, no duplicados — enfocate en la experiencia de uso: qué
ve, qué click hace, qué feedback recibe).

### Plantilla — historia de usuario (Connextra + Gherkin), ejemplo desde UI/UX

```markdown
## HU-F01: Ver y pagar una multa desde la interfaz

**Como** lector con una multa pendiente,
**quiero** ver el detalle de mi multa y poder pagarla desde la
aplicación web,
**para** regularizar mi situación y volver a poder pedir libros
prestados sin tener que llamar o ir presencialmente a preguntar.

### Criterios de aceptación (Gherkin)

```gherkin
Característica: Visualización y pago de multas (vista lector)

  Escenario: El lector ve su multa pendiente con el monto correcto
    Dado que el lector "juan@uteq.edu.ec" tiene una multa con estado PENDIENTE
    Cuando el lector abre la sección "Mis multas"
    Entonces ve el monto, la fecha en que se generó, y el estado "Pendiente"

  Escenario: El lector no puede pagar su propia multa desde la UI
    Dado que el lector "juan@uteq.edu.ec" está en la sección "Mis multas"
    Entonces no ve ningún botón de "Pagar" ni "Anular"
    Y ve un mensaje indicando que debe acercarse a la biblioteca para regularizarla

  Escenario: El bibliotecario paga una multa desde su vista de gestión
    Dado que el bibliotecario está en la sección "Gestión de multas"
    Y selecciona la multa pendiente de "juan@uteq.edu.ec"
    Cuando hace clic en "Pagar" y confirma
    Entonces la multa cambia a estado "Pagada" en la lista
    Y si era la única multa pendiente del lector, dejaría de estar bloqueado (verificable en un siguiente intento de préstamo)
```
```

### Plantilla — caso de uso (Cockburn), ejemplo desde UI/UX

```markdown
## CU-F01: Pagar una multa desde la vista de gestión (bibliotecario)

- **Actor principal**: Bibliotecario (usando la interfaz web).
- **Interesados y sus intereses**:
  - Bibliotecario: quiere completar el cobro en pocos clics, sin
    recargar la página ni perder el filtro/paginación en el que estaba.
  - Lector: quiere que su bloqueo se levante inmediatamente después del
    pago, sin demoras.
- **Precondiciones**: el bibliotecario tiene sesión iniciada; existe al
  menos una multa en estado PENDIENTE visible en la lista.
- **Garantía de éxito**: la fila de la multa en la tabla se actualiza a
  "Pagada" sin recargar toda la página; si el pago desbloqueó al
  usuario, no hace falta ninguna acción adicional en la UI (el backend
  ya lo resuelve).
- **Disparador**: el bibliotecario hace clic en "Pagar" en la fila de
  una multa pendiente.

### Escenario principal (flujo básico)

1. El bibliotecario ubica la multa pendiente en la tabla paginada de
   "Gestión de multas".
2. Hace clic en el botón "Pagar" de esa fila.
3. La interfaz muestra una confirmación simple (¿está seguro?).
4. El bibliotecario confirma.
5. La interfaz llama a `POST /api/v1/multas/{id}/pago`.
6. La fila se actualiza a estado "Pagada" sin recargar el resto de la
   tabla ni perder la página actual de paginación.

### Extensiones (flujos alternativos)

- **5a.** La multa ya estaba pagada/anulada (otro bibliotecario la
  procesó en paralelo): el backend responde 409; la UI muestra
  `errorMsg` ("Esta multa ya fue procesada") y refresca la fila para
  reflejar el estado real, en vez de dejar la fila desactualizada.
- **1a.** No hay multas pendientes: la tabla muestra un mensaje vacío
  ("No hay multas pendientes") en vez de una tabla en blanco sin
  explicación.
```

Replicá esta estructura para el resto de tus flujos (al menos: ver
préstamos propios, crear reservación desde la UI, gestión de
préstamos/devoluciones desde la vista de bibliotecario — 4 historias +
4 casos de uso como mínimo, complementando los de Cajas sin duplicarlos).
