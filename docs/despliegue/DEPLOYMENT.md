# Despliegue de producción — SGB-SaaS

> Documento base del despliegue oficial del sistema para la Entrega Final
> (guía, Bloque A.4 / criterio P5 de la rúbrica). Complementos operativos:
> [RUNBOOK.md](RUNBOOK.md) (operación de día a día) y [BACKUP.md](BACKUP.md)
> (respaldo y restauración).

## 0. Estado de despliegue: sin confirmar

**No se afirma como hecho vigente cuál de los dos estados de abajo está
realmente aplicado en el dashboard de Render ahora mismo** — nadie del
equipo lo ha confirmado todavía, así que no se adivina.

- **Estado documentado en esta rama** (verificado con `curl -I` contra la
  URL pública real el 2026-08-14): el frontend corre como **Static
  Site**, con cabeceras de seguridad cargadas manualmente en el Dashboard
  de Render (§5.4.1). Este documento **nunca menciona una corrección para
  el fallback de rutas SPA** (`/login` y rutas equivalentes devolviendo
  `404`) — no hay evidencia en esta rama de que ese problema se haya
  resuelto.
- **Corrección propuesta en `main`** (rama `conf-produccion`, añadida el
  2026-08-15/16, ver §6 más abajo): `render.yaml` fuerza `runtime: docker`
  para frontend y backend, específicamente para resolver el 404 de rutas
  SPA — diagnosticado ahí como causado por el propio comportamiento de
  Static Site. Este merge trae `render.yaml` a esta rama por primera vez,
  pero **aplicarlo requiere una acción manual en el dashboard de Render**
  (crear un Blueprint o migrar el servicio existente) — el archivo estar
  en el repositorio no reconfigura un servicio ya creado por su cuenta.

Quien confirme el estado real en el dashboard debería actualizar esta
sección quitando la ambigüedad, en vez de que quede así indefinidamente.

## 1. Arquitectura desplegada (resumen ejecutivo)

El sistema corre en tres proveedores PaaS/SaaS independientes, todos en
plan **free** y sin expiración (salvo los límites de uso del propio plan):

| Capa | Proveedor / servicio | URL real |
|---|---|---|
| Frontend (SPA Angular) | **Render — Static Site** | https://biblora-sgb.onrender.com |
| Backend (API REST Spring Boot) | **Render — Web Service** | https://sgb-backend-b058.onrender.com |
| Base de datos | **Neon — Postgres** (plan Free) | (pooled/session URL provista por el dashboard de Neon) |
| Caché y auth (blacklist JWT) | **Upstash — Redis** (plan Free) | (REST URL provista por el dashboard de Upstash) |

HTTPS es provisto automáticamente por Render en ambos servicios públicos
(`*.onrender.com` con TLS), sin certificados gestionados a mano.

> **Historial de la decisión:** el plan original de esta rama contemplaba
> una VM propia en Oracle Cloud (Always Free ARM) con nginx + Certbot
> administrados manualmente. Esa vía se descartó por saturación de
> capacidad en las instancias ARM del Always Free de Oracle Cloud. La
> arquitectura final, validada de punta a punta, es la de esta tabla. Ver
> la Actualización fechada en `docs/adr/adr-007-estrategia-despliegue.md`.

## 2. Recursos consumidos por el plan free (estado vigente a agosto 2026)

Cifras verificadas contra la documentación oficial de cada proveedor en la
fecha indicada; los proveedores pueden cambiarlas, re-verificar antes de
cualquier decisión que dependa de ellas.

### 2.1 Render (plan Free, sin tarjeta de crédito)

- **Web Service (backend):** instancia de 512 MB RAM / 0.1 CPU.
  - Dormita (*spin down*) tras **15 minutos sin tráfico** entrante; la
    primera petición siguiente sufre un *cold start* de ~30–60 s mientras
    Render reactiva el contenedor.
  - Límite de **750 horas de instancia por mes** por workspace
    (720 h = ~1 instancia 24/7; dormir no consume horas).
- **Static Site (frontend):** sin costo y **sin límite de tiempo** (no
  duerme, se sirve desde CDN); consume del cuota mensual del workspace:
  100 GB de ancho de salida y 500 minutos de build compartidos.
- Los despliegues con Docker (backend) consumen minutos de build de la
  misma cuota de 500 min/mes.

### 2.2 Neon (Postgres — plan Free, sin expiración)

- **0.5 GB** de almacenamiento de datos.
- **100 CU-horas / mes** de cómputo (se duplicó de 50 a 100 en octubre
  2025); el cómputo escala a cero tras **5 min sin actividad**, por lo que
  un uso intermitente académico no agota el cupo.
- **PITR**: ventana de **6 horas** para restauración punto-en-el-tiempo,
  con tope de 1 GB de historial de cambios, sin cargo (ver
  [BACKUP.md](BACKUP.md) — con 6 h, el respaldo de largo plazo se cubre
  con snapshots manuales, ver §5).
- Branching incluido en todos los planes (10 ramas incluidas por proyecto).

### 2.3 Upstash (Redis — plan Free, sin expiración)

- **500.000 comandos por mes** (límite vigente desde marzo 2025; reemplazó
  al antiguo tope diario de 10.000 comandos/día que aún citan muchas guías
  viejas), **256 MB** de datos, **10 GB** de ancho de banda/mes.
- Los AUTH/HELLO/PING administrativos no se cobran; el consumo real lo
  dominan el rate limiting de login y la blacklist JWT.

## 3. Topología simplificada

```
                              Internet
                                 │
              ┌──────────────────┴──────────────────┐
              │                                     │
   ┌──────────▼──────────┐                ┌─────────▼──────────┐
   │ Render Static Site  │   CORS/HTTPS   │ Render Web Service │
   │ (frontend Angular)  │───────────────▶│ (backend Spring    │
   │ biblora-sgb.onrender│   fetch/api    │  Boot + Flyway)    │
   └─────────────────────┘                │ sgb-backend-...    │
                                          └───────┬────────┬───┘
                                          HTTPS   │        │ HTTPS
                                ┌─────────────────┘        └──────────────┐
                                │                                        │
                      ┌─────────▼─────────┐                     ┌────────▼─────────┐
                      │ Neon Postgres     │                     │ Upstash Redis    │
                      │ (free, 0.5 GB)    │                     │ (free, blacklist │
                      │                   │                     │  JWT + rate lim) │
                      └───────────────────┘                     └──────────────────┘
```

Puntos clave del diagrama:

- **No hay red privada compartida.** A diferencia del stack local de
  Docker Compose (donde `frontend`, `backend`, `postgres` y `redis`
  comparten una red de bridge), en producción **cada servicio se conecta
  a los demás por URL pública con TLS**: el navegador → Static Site de
  Render; el backend → endpoint de conexión de Neon (host
  `ep-*.aws.neon.tech`) y → endpoint REST de Upstash (host
  `*.upstash.io`).
- La única pieza que Render "une" dentro de su plataforma es el CORS del
  backend (`SecurityConfig`), que permite el origen exacto
  `https://biblora-sgb.onrender.com` (ver commit `9adbada`).
- El frontend está construido apuntando a la URL pública del backend
  (no usa proxy local); en el bundle entregado por el Static Site, la API
  es `https://sgb-backend-b058.onrender.com/api/v1` (commit `45707be` y
  derivados).

## 4. Variables de entorno de producción

Solo nombres y propósito; **ningún valor real** se documenta ni se
committea. Se cargan en **Environment** del Web Service en Render (están
marcadas las que son gestionadas por Render automáticamente).

| Variable | Proveedor | Propósito |
|---|---|---|
| `PORT` | Render (automática) | Puerto HTTP interno donde Render enruta el tráfico al contenedor |
| `DB_URL` | SGB | JDBC URL de Neon (`jdbc:postgresql://...`) |
| `DB_USER` | SGB | Usuario de la base Neon |
| `DB_PASSWORD` | SGB | Password del usuario de Neon |
| `REDIS_HOST` | SGB | Host del endpoint de Upstash |
| `REDIS_PORT` | SGB | Puerto TLS del endpoint de Upstash (normalmente 6379) |
| `REDIS_PASSWORD` | SGB | Token REST de Upstash (se usa como password) |
| `REDIS_SSL` | SGB | `true` — Upstash solo acepta conexiones TLS |
| `JWT_SECRET` | SGB | Clave HS256 (≥256 bits); ver RUNBOOK §Rotación de secretos |
| `SPRING_PROFILES_ACTIVE` | SGB | `prod` — deshabilita Swagger UI y oculta stacktraces (ver adr-015) |
| `SPRING_FLYWAY_LOCATIONS` | SGB | Ruta de las migraciones de Flyway (mismo valor que local) |
| `JAVA_TOOL_OPTIONS` | SGB | Flags de la JVM para acotar memoria al free tier (512 MB) |
| `CACHE_LIBROS_TTL_SECONDS` | SGB | TTL del caché Redis de listado de libros (consumo Upstash) |
| `LOGIN_RATE_LIMIT_WINDOW_SECONDS` | SGB | Ventana del rate limiting de login |
| `LOGIN_MAX_ATTEMPTS` | SGB | Intentos máximos antes de responder 429 |

Reglas de despliegue: marcar **"Do not print"** en Render para las
variables secretas (`DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`), y
ajustar `JAVA_TOOL_OPTIONS` (p. ej. `-XX:MaxRAMPercentage=75`) para no
exceder la RAM del plan Free. El resto de variables del `.env.example`
(`SMTP_*`, `GEMINI_API_KEY`, etc.) se carga solo si el módulo
correspondiente está habilitado en el despliegue.

## 5. Procedimiento paso a paso para reproducir el despliegue desde cero

Estados previos: cuenta de GitHub con el repo `mloorm14/sgb-saas` y acceso
de push, y las credenciales SMTP/Gemini si se quiere habilitar ese módulo.
**Sin tarjeta de crédito en ninguno de los tres proveedores.**

### 5.1 Neon — crear la base de datos

1. Crear cuenta en https://neon.tech (login con GitHub/Google).
2. **New project**: nombre `sgbdb`, región cercana (p. ej. Frankfurt),
   plan **Free**.
3. En **Connection Details** copiar la cadena **pooled** con usuario
   `neondb_owner` y su password; del host se deriva `DB_URL`, `DB_USER` y
   `DB_PASSWORD` para Render.
4. Opcional, recomendado: en **Settings → History** dejar el history
   window por defecto de 6 h (plan Free).

### 5.2 Upstash — crear el Redis

1. Crear cuenta en https://upstash.com (login con GitHub/Google), plan
   **Free** (sin tarjeta).
2. **Create database**: nombre `sgb-redis`, región compatible, TLS
   habilitado.
3. En la pestaña **Details** copiar el endpoint host, puerto y el token
   REST → `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (`REDIS_SSL=true`).

### 5.3 Render — desplegar el backend (Web Service)

1. Crear cuenta en https://render.com (login con GitHub/Google), plan
   **Free**.
2. **New → Web Service**: conectar el repo `mloorm14/sgb-saas`,
   rama `feature/despliegue-produccion` (rama de despliegue actual del
   servicio en Render — posición cambiada desde `conf-produccion` para
   incorporar las migraciones V11/V12 del usuario demo), que contiene el
   `Dockerfile` multi-stage del
   backend (el servicio se construye por Docker).
   - Build: `docker build -f backend-springboot/Dockerfile` (o raíz del
     repo según la config desplegada — el despliegue funcional actual
     usa el Dockerfile de `backend-springboot`), Start command igual al
     del Dockerfile.
3. **Instance type: Free** (512 MB RAM / 0.1 CPU).
4. **Environment**: cargar las variables de la §4 (nombres y valores del
   paso 5.1/5.2), secretos marcados "Do not print".
5. **Advanced**: `SPRING_PROFILES_ACTIVE=prod` y `JAVA_TOOL_OPTIONS` de
   memoria; dejar el resto por defecto.
6. **Create Web Service** → Render construye, despliega y emite
   `https://sgb-backend-XXXX.onrender.com` con HTTPS automático.
7. Primera verificación (puede tardar porque Flyway corre en el primer
   arranque y el Web Service despierta en frío):
   ```bash
   curl -s https://<tu-backend>.onrender.com/actuator/health
   # {"status":"UP"} (o UP con componentes db/redis incluidas)
   ```

### 5.4 Render — desplegar el frontend (Static Site)

1. **New → Static Site**: mismo repo, rama `feature/despliegue-produccion`
   (misma posición que el Web Service, para mantener frontend y backend
   en el mismo punto).
2. Build command: `npm run build --prefix frontend-angular` (equivale al
   `<build>` del entorno de CI), Publish directory:
   `frontend-angular/dist/`.
3. El bracket del bundle ya apunta a la URL pública del backend
   (`https://sgb-backend-b058.onrender.com/api/v1`); si el host del Web
   Service difiere, editar los `environment`/URLs de servicio del módulo
   correspondiente y reconstruir.
4. **Create Static Site** → Render sirve el sitio con HTTPS automático y
   sin dormir.

#### 5.4.1 Cabeceras de seguridad del Static Site (Dashboard de Render)

**Render no soporta el archivo `_headers` estilo Netlify en Static
Sites**: se comprobó el 2026-08-14 que Render lo sirve como archivo
estático plano (descargable en `/_headers`) y no lo interpreta como
configuración. Las cabeceras personalizadas del Static Site se definen en
el **Dashboard de Render** (Servicio → pestaña **Headers**), no en el
repositorio.

Configuración actual (servicio `biblora-sgb`, verificada con
`curl -I https://biblora-sgb.onrender.com/` el 2026-08-14):

| Cabecera | Valor cargado en el Dashboard (patrón `/*`) |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; script-src-attr 'none'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' https://sgb-backend-b058.onrender.com; frame-ancestors 'none'; base-uri 'self'; form-action 'none'; object-src 'none'` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Strict-Transport-Security` | automática del edge de Render (`max-age=315360000; includeSubdomains; preload`) — no requiere configuración propia en Static Sites |
| `Cross-Origin-Opener-Policy` | `same-origin` |
| `Cross-Origin-Resource-Policy` | `same-origin` |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=()` |

> **Nota operativa (importante):** esta configuración **no vive en el
> repositorio**. Si el servicio del frontend se recrea o migra a otro
> proveedor, se pierde y hay que volver a cargarla manualmente desde la
> tabla de arriba.
>
> **Nota sobre la CSP 2026-08-14:** la política cargada en el Dashboard fue
> endurecida agregando `script-src-attr 'none'` y `form-action 'none'`
> (auditoría previa confirmó que los 5 formularios de la app usan
> `(ngSubmit)` + `HttpClient`, ninguno depende de submit nativo; con
> `form-action 'none'` la app no rompe). Esto cerró el ítem `Failure to
> Define Directive with No Fallback` del plugin 10055 de ZAP (reporte v2:
> 0 High, 1 Medium restante `style-src 'unsafe-inline'` — aceptado, ver
> `docs/mediciones/sec/owasp/README.md`).
>
> **Nota COEP 2026-08-14:** `Cross-Origin-Embedder-Policy` fue **evaluado
> y descartado deliberadamente** (no por descuido): `require-corp` exigiría
> `Cross-Origin-Resource-Policy` en los subrecursos del backend y bloquearía
> las llamadas cross-origin del SPA al API (`https://sgb-backend-b058.onrender.com`).
> Beneficio marginal frente al riesgo de romper el producto; registrado como
> línea de trabajo futuro (`credentialless`) si cambia la arquitectura.
>
> El archivo `frontend-angular/public/_headers` se conserva solo como
> referencia histórica (ver `frontend-angular/public/README.md`).

Notas de diseño (no cambiar sin releer):
- `style-src ... 'unsafe-inline'` es **obligatorio**: Angular inyecta
  estilos inline en runtime (Critters además inlinea el CSS crítico en el
  `index.html`); sin `'unsafe-inline'` la SPA no carga. Riesgo aceptado
  y documentado en `docs/mediciones/sec/owasp/README.md`.
- `connect-src` incluye explícitamente el host del backend, que es un
  origen distinto al del frontend.
- Si el proyecto cambia de backend (host de Render distinto), editar
  `connect-src` a la vez que las URLs de servicio del bundle.
- `Strict-Transport-Security` la agrega el edge de Render en
  `*.onrender.com` automáticamente; por eso no se carga manualmente en el
  Dashboard (evitar cabecera duplicada o contradictoria).
- `Cross-Origin-Opener-Policy: same-origin` y `Cross-Origin-Resource-Policy:
  same-origin` aíslan la ventana y los subrecursos sin afectar el `fetch`
  cross-origin al backend (CORP solo restringe la carga de subrecursos del
  documento, no las peticiones `fetch`/`XHR`). Se eligió no agregar COEP
  (ver nota arriba).
- `Permissions-Policy` bloquea `geolocation`, `microphone`, `camera` y
  `payment` (la app no usa ninguna de esas APIs; si un futuro
  feature las necesita, hay que ampliar la lista antes del deploy).

#### 5.4.2 Dependencias del frontend (Angular)

El bundle de producción se construye con **Angular 21.2.20** (subido
desde 17.3 en la rama `feature/seguridad-headers-owasp-zap` por CVEs de
2026 en `@angular/core` ≤ 18.2.14 — ver
`docs/mediciones/sec/owasp/README.md`). Actualizar `@angular/*`,
`zone.js` y `typescript` juntos (versionado enlazado); `ng update` exige
una major por paso para migraciones automáticas.

### 5.5 Verificación integral (punto a punto)

1. `curl https://<backend>.onrender.com/actuator/health` → `UP`.
2. Abrir `https://<frontend>.onrender.com` en el navegador: carga la SPA.
3. `POST https://<backend>.onrender.com/api/auth/login` con credenciales
   válidas → 200 con `accessToken`; repetir con password incorrecto 5
   veces y confirmar `429` (rate limiting vía Upstash).
   NOTA: el módulo de autenticación mapea en `/api/auth` (sin
   `v1` — ver `AuthController` y `frontend-angular/.../auth.service.ts`);
   el `/api/v1` es solo para los módulos de negocio.
4. En los dashboards de Neon y Upstash confirmar conexiones/uso en vivo
   (prueba de que el backend llega a ambos por URL pública TLS).
5. Si el primer acceso tarda ~60 s, es el *cold start* del plan Free
   (comportamiento esperado, §2.1).

## 6. Historial de incidentes documentados en `main` (rama `conf-produccion`, 2026-08-15/16)

Esta sección proviene de `main` (fecha de escritura posterior a todo el
resto de este documento) y no fue verificada de nuevo contra esta rama —
se conserva tal cual porque documenta hallazgos reales con evidencia
propia, no se descarta solo por venir de otra rama. Ver §0 para la
salvedad sobre si esta corrección específica (404 de rutas SPA) está
realmente aplicada hoy.

### 6.1 Problema conocido: 404 en rutas SPA del frontend

**Síntoma observado** (verificado con `curl -I` contra la URL pública
real el 2026-08-15): `GET https://biblora-sgb.onrender.com/` responde
`200 OK` con contenido real; `GET https://biblora-sgb.onrender.com/login`
responde `404 Not Found`.

**Causa raíz más probable.** El servicio de Render que sirve el
frontend está configurado como **Static Site** (sirve los archivos del
build directamente, sin pasar por el `Dockerfile`/`nginx.conf` de este
repositorio) en vez de como **Web Service con Runtime Docker**. Un
Static Site sin una regla de reescritura de rutas explícita devuelve
`404` para cualquier ruta que no sea un archivo real en el build (como
`/login`, que solo existe como ruta de Angular, no como archivo
`login.html`) — comportamiento esperado de ese tipo de servicio, no un
bug del código Angular.

**Corrección aplicada en `main`:**

1. `render.yaml` (raíz del repo) fuerza `runtime: docker` para ambos
   servicios, para que Render use el `Dockerfile` real (y por lo tanto
   `nginx.conf`, que sí tiene `try_files $uri $uri/ /index.html;`) en
   vez de tratar al frontend como Static Site. Esta es la corrección
   principal — pero ver §0: traer el archivo a una rama no reconfigura
   un servicio de Render ya creado, se necesita una acción manual además.
2. `frontend-angular/public/_redirects` (contenido: `/* /index.html
   200`) se agrega como red de seguridad adicional, por si el equipo
   decide NO migrar a `render.yaml` y mantener el servicio como Static
   Site manual — ese formato de archivo es el mecanismo de fallback SPA
   que reconocen los Static Site de Render (heredado de la convención de
   Netlify).
3. **Limitación honesta de la corrección #2**: `angular.json` no incluye
   `public/` en su arreglo `assets`, así que `ng build` no copia
   `_redirects` automáticamente. `frontend-angular/Dockerfile` lo copia
   explícitamente al output de nginx — pero esa copia solo ocurre si el
   servicio efectivamente usa el Dockerfile (corrección #1). La única
   corrección que resuelve el problema en ambos escenarios (Docker o
   Static Site) es la #1; la #2/#3 quedan como defensa adicional.

### 6.2 Hallazgo (corregido en un commit posterior): URL del backend hardcodeada en el frontend

En un commit base anterior de `main` (`cd25ebe`), **6 archivos** del
frontend tenían la URL del backend hardcodeada como
`http://localhost:8080/api` (o `/api/v1`), sin pasar por ningún mecanismo
de configuración: `auth.service.ts`, `libros.component.ts`,
`multas.component.ts`, `prestamos-gestion.component.ts`,
`prestamos-lector.component.ts`, `reservaciones.component.ts`. Esto
significaba que el JavaScript que corre en el navegador del evaluador
intentaba llamar a `http://localhost:8080` (la máquina del propio
evaluador, no el backend real).

**Corregido** con el mecanismo estándar de Angular
(`fileReplacements`): `environment.ts` (desarrollo, `apiUrl:
'http://localhost:8080/api'`) vs `environment.prod.ts` (producción,
`apiUrl: 'https://sgb-backend-b058.onrender.com/api'`), intercambiados
por `angular.json` según la configuración de build. Verificado con
`grep` sobre los bundles generados que el bundle de producción no
contiene `localhost:8080` y el de desarrollo no contiene la URL de
producción. Esta rama (`interfaces-completas`) ya usaba internamente
servicios (`LibroService`, `MultaService`, etc.) construidos sobre
`environment.apiUrl` desde antes — el mecanismo coincide, no hubo
conflicto real al traer este fix.

### 6.3 Navegador para los tests (Karma/Puppeteer)

`frontend-angular/karma.conf.js` resuelve `CHROME_BIN` en este orden:
(1) variable ya seteada en el entorno, (2) un Chrome/Edge/Chromium ya
instalado en el sistema, (3) como último recurso, el Chromium que
instala `puppeteer`. `frontend-angular/.puppeteerrc.cjs` fija
`skipDownload: true` por defecto para que `npm ci`/`npm install` nunca
dependan de una descarga externa (`storage.googleapis.com`) que puede
estar bloqueada por el firewall de una red institucional — un fallo ahí
antes hacía fallar `npm ci` completo (bloqueando también `ng build`), no
solo los tests.

## 7. Referencias

- [RUNBOOK.md](RUNBOOK.md) — operación, rotación de secretos y redeploys.
- [BACKUP.md](BACKUP.md) — respaldo Neon, retención mínima y prueba de
  restauración.
- [ADR-007](docs/adr/adr-007-estrategia-despliegue.md) — decisión y
  Actualización a esta arquitectura.
- `render.yaml` (raíz del repo, traído por primera vez a esta rama en
  este merge — ver §0 y §6.1).
- `frontend-angular/karma.conf.js`, `frontend-angular/.puppeteerrc.cjs`.
- Documentación oficial de límites verificada: `render.com/docs/free`,
  `neon.com/docs/introduction/plans`, `upstash.com/pricing/redis`
  (agosto 2026).