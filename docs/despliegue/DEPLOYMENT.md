# Despliegue — SGB-SaaS (Bloque A.4.2)

Documenta el despliegue público real del sistema. Escrito con el mismo
criterio de honestidad que el resto de `docs/`: lo verificable contra el
repositorio o contra una respuesta HTTP real se declara como tal; lo que
no se pudo confirmar desde este entorno queda marcado explícitamente
como `<PENDIENTE>`, no se inventa.

**Fecha**: 2026-08-15. **Commit base**: `cd25ebe3ad838c94f9b371b65ca39d550021fb1d`.

## 1. Proveedor

**Render**, plan **free tier** (confirmado por el contexto del proyecto;
no hay forma de confirmar el plan exacto desde el repositorio —
verificar en el dashboard de Render si se necesita el nombre literal del
plan).

## 2. Topología

Dos servicios web independientes, más base de datos y caché externos:

| Componente | Tecnología | Dónde corre |
|---|---|---|
| Backend | Spring Boot (Java 21), imagen Docker (`backend-springboot/Dockerfile`) | Servicio Web en Render |
| Frontend | Angular + nginx, imagen Docker (`frontend-angular/Dockerfile`) | Servicio Web en Render |
| Base de datos | PostgreSQL | Gestionada externa — ver nota abajo |
| Caché / blacklist JWT | Redis | Gestionada externa — ver nota abajo |

**Nota de honestidad sobre Neon/Upstash.** `README.md` afirma que la
base de datos usa **Neon** y el caché usa **Upstash**. Se revisó el
repositorio completo para esta tarea buscando una confirmación
independiente de esa afirmación (`docker-compose.yml`, `.env.example`,
`.github/workflows/`, cualquier archivo `.yml`/`.yaml` del repo) y
**no se encontró ninguna referencia a "neon" ni "upstash" fuera de esa
misma línea de `README.md`** -- ni una cadena de conexión, ni un
comentario, ni una variable con ese nombre. La afirmación puede ser
correcta (viene de contexto directo del equipo, no se está diciendo que
sea falsa), pero **no está verificada contra ninguna evidencia dentro
del repositorio** -- se documenta aquí tal como está, con esta salvedad
explícita, en vez de presentarla como un hecho confirmado por el código.
Quien confirme el proveedor real de base de datos/caché en el dashboard
de Render debería actualizar esta nota quitando la salvedad.

## 3. Recursos consumidos

`<PENDIENTE: confirmar en el dashboard de Render — plan exacto, CPU,
RAM, disco, y si el free tier tiene el comportamiento de "sleep" tras
inactividad activo para cada servicio>`. No hay forma de obtener esta
información desde el repositorio ni desde una petición HTTP externa; se
deja como placeholder explícito en vez de estimarla.

## 4. Variables de entorno de producción

Solo nombres -- **ningún valor real ni secreto se documenta aquí**. Lista
construida cruzando `.env.example` (raíz del repo) contra los
placeholders `${...}` realmente usados en
`backend-springboot/src/main/resources/application.yml`, no solo copiada
de uno de los dos archivos.

### Backend (`sgb-backend` en `render.yaml`)

| Variable | Tipo | Origen |
|---|---|---|
| `JWT_SECRET` | Secreto | `.env.example` |
| `DB_URL` | Específica del entorno | `.env.example` |
| `DB_USER` | Específica del entorno | `.env.example` |
| `DB_PASSWORD` | Secreto | `.env.example` |
| `REDIS_HOST` | Específica del entorno | `.env.example` |
| `REDIS_PORT` | Específica del entorno | `.env.example` |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` | Secreto (user/pass) / configuración | `.env.example` |
| `GEMINI_API_KEY` | Secreto | `.env.example` |
| `SPRING_PROFILES_ACTIVE` | Configuración (valor fijo `prod` en `render.yaml`) | `.env.example`, `docker-compose.yml` |
| `SPRING_FLYWAY_LOCATIONS` | Configuración (valor fijo en `render.yaml`) | `docker-compose.yml` (no está en `.env.example`) |
| `CACHE_LIBROS_TTL_SECONDS`, `CACHE_SUGERENCIAS_TTL_SECONDS` | Configuración, con default | `.env.example` |
| `LOGIN_RATE_LIMIT_WINDOW_SECONDS`, `LOGIN_MAX_ATTEMPTS` | Configuración, con default | `.env.example` |

**Variables adicionales encontradas en `application.yml` que
`.env.example` NO documenta** (todas tienen un valor por defecto seguro
en el propio `application.yml`, por eso `render.yaml` no las declara
explícitamente -- se listan aquí por completitud, no por ser un
problema):
`GEMINI_MODELO`, `GEMINI_TIMEOUT_MS`, `GEMINI_URL_BASE`,
`CHATBOT_RATE_LIMIT_MAX_MENSAJES`, `CHATBOT_RATE_LIMIT_WINDOW_SECONDS`,
`NOTIF_MINUTOS_ANTICIPACION_VENCIMIENTO`,
`VERIFICACION_CORREO_TTL_MINUTES`.

**Discrepancia encontrada en `.env.example` (no corregida en esta
tarea, fuera de su alcance -- solo declarada).** El comentario de
`.env.example` sobre `SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/`SMTP_PASS`
dice *"Todavía NO consumidas por application.yml"*. Verificado
directamente contra `application.yml` para esta tarea: **sí están
consumidas** (`${SMTP_HOST:localhost}`, `${SMTP_PORT:587}`,
`${SMTP_USER:}`, `${SMTP_PASS:}`) -- el comentario de `.env.example`
quedó desactualizado en algún momento posterior a escribirse.

### Frontend (`biblora-sgb` en `render.yaml`)

**Ninguna.** Verificado directamente en `frontend-angular/src/` para
esta tarea: el proyecto **no tiene** `environment.ts` ni
`environment.prod.ts`, ni ningún otro mecanismo de configuración en
tiempo de build o de arranque (sin `config.json` cargado en runtime, sin
`APP_INITIALIZER` que lea variables, sin CSP declarada en un `<meta>` de
`index.html`). Ver la sección 6 para el hallazgo relacionado.

## 5. Procedimiento de despliegue (reproducible con `render.yaml`)

1. En el dashboard de Render, crear un nuevo **Blueprint** apuntando a
   este repositorio (`github.com/mloorm14/sgb-saas`), rama `main`.
   Render detecta automáticamente `render.yaml` en la raíz.
2. Render propondrá crear los 2 servicios (`sgb-backend`, `biblora-sgb`)
   definidos en `render.yaml`, ambos `runtime: docker`. Confirmar la
   creación.
3. Completar en el dashboard, para `sgb-backend`, las variables marcadas
   `sync: false` en `render.yaml` (`JWT_SECRET`, `DB_URL`, `DB_USER`,
   `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `SMTP_HOST`, `SMTP_USER`,
   `SMTP_PASS`, `GEMINI_API_KEY`) con los valores reales de producción
   -- **nunca** los valores de desarrollo de `.env.example`.
4. Esperar el build de ambos servicios (Render construye cada Dockerfile
   con el `dockerContext` indicado -- raíz del repo para el backend,
   `frontend-angular/` para el frontend).
5. Verificar `GET <url-backend>/actuator/health` responde `200` antes de
   dar por buena la migración Flyway automática al arrancar.
6. Verificar `GET <url-frontend>/` y `GET <url-frontend>/login` (o
   cualquier ruta profunda del SPA) responden `200` -- esta es
   exactamente la verificación que fallaba con el servicio como Static
   Site (ver sección 6).

**Si el equipo NO migra a `render.yaml`** y mantiene la configuración
manual actual del dashboard: aplicar como mínimo la corrección de la
sección 6 (recrear el servicio del frontend como *Web Service* con
*Runtime* Docker, no *Static Site*) -- Render no permite cambiar el tipo
de un servicio ya creado, esto es conocimiento general de la plataforma,
no algo verificado contra este repositorio.

## 6. Problema conocido: 404 en rutas SPA del frontend

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
`login.html`) -- comportamiento esperado de ese tipo de servicio, no un
bug del código Angular.

**Evidencia adicional encontrada durante esta tarea que refuerza esta
hipótesis** (no estaba en el diagnóstico previo): la respuesta real de
`GET /` incluye una cabecera `Content-Security-Policy` completa y
específica (con `connect-src ... https://sgb-backend-b058.onrender.com`
codificado explícitamente). `frontend-angular/nginx.conf`, el único
archivo de configuración de nginx que existe en este repositorio, **no
define ningún header `Content-Security-Policy`** -- solo
`X-Frame-Options` y `X-Content-Type-Options`. Que la respuesta real
traiga una cabecera que el nginx.conf versionado no genera es
consistente con que el servicio en producción no esté usando ese
`nginx.conf` en absoluto (es decir, no está corriendo el Dockerfile) --
la cabecera se configuró aparte, directamente en el dashboard de Render,
un mecanismo disponible para servicios Static Site.

**Corrección aplicada en este commit:**

1. `render.yaml` (raíz del repo) fuerza `runtime: docker` para ambos
   servicios, para que Render use el `Dockerfile` real (y por lo tanto
   `nginx.conf`, que sí tiene `try_files $uri $uri/ /index.html;`) en
   vez de tratar al frontend como Static Site. Esta es la corrección
   principal.
2. `frontend-angular/public/_redirects` (contenido: `/* /index.html
   200`) se agrega como red de seguridad adicional, por si el equipo
   decide NO migrar a `render.yaml` y mantener el servicio como Static
   Site manual -- ese formato de archivo es el mecanismo de fallback SPA
   que reconocen los Static Site de Render (heredado de la convención de
   Netlify).
3. **Limitación honesta de la corrección #2**: se verificó
   `frontend-angular/angular.json` y su arreglo `assets` **no** incluye
   la carpeta `public/` -- `ng build` (Angular 17.3, builder
   `application`) no copia automáticamente `public/_redirects` a
   `dist/frontend-angular/browser/` con la configuración actual. Se
   ajustó `frontend-angular/Dockerfile` para copiar ese archivo
   explícitamente al output de nginx (`COPY public/_redirects
   /usr/share/nginx/html/_redirects`) -- pero esa copia **solo ocurre
   si el servicio efectivamente usa el Dockerfile** (Web Service/Docker,
   la corrección #1). Si el equipo mantiene el servicio como Static Site
   *sin* aplicar `render.yaml`, `_redirects` **no** llegará al build que
   Render sirve, porque el build de Static Site corre `ng build`
   directamente (sin Docker) y ese comando tampoco copiaría `public/`
   con la configuración actual de `angular.json`. La única corrección
   que resuelve el problema en ambos escenarios (Docker o Static Site)
   es la #1 (`render.yaml`); la #2/#3 quedan documentadas como defensa
   adicional, no como solución independiente para el caso Static Site.

## 7. Hallazgo (encontrado en esta tarea, corregido en la siguiente): URL del backend hardcodeada en el frontend

Al revisar `frontend-angular/src/` para completar la sección 4 (variables
de entorno del frontend) se encontró que **6 archivos** del frontend
tenían la URL del backend **hardcodeada como
`http://localhost:8080/api`** (o `/api/v1`), sin pasar por ningún
mecanismo de configuración:

- `src/app/core/services/auth.service.ts`
- `src/app/libros/libros.component.ts`
- `src/app/multas/multas.component.ts`
- `src/app/prestamos-gestion/prestamos-gestion.component.ts`
- `src/app/prestamos-lector/prestamos-lector.component.ts`
- `src/app/reservaciones/reservaciones.component.ts`

Esto significaba que, tal como estaba el código fuente en el commit base
de este documento (`cd25ebe`), el JavaScript que corre en el navegador
del evaluador intentaba llamar a `http://localhost:8080` (la máquina del
propio evaluador, no el backend real) -- estas llamadas fallaban
independientemente de que se corrigiera el problema de la sección 6. Se
documentó como hallazgo en ese commit sin corregirlo (fuera del alcance
de esa tarea, que era explícitamente de despliegue/infraestructura). Se
corrigió en un commit posterior sobre este mismo repositorio -- ver
sección 8.

## 8. Configuración de entorno del frontend (`fileReplacements`)

**Corregido.** El hallazgo de la sección 7 se resolvió con el mecanismo
estándar de Angular para variables de configuración en tiempo de build:

- `frontend-angular/src/environments/environment.ts` (desarrollo):
  `apiUrl: 'http://localhost:8080/api'`.
- `frontend-angular/src/environments/environment.prod.ts` (producción):
  `apiUrl: 'https://sgb-backend-b058.onrender.com/api'`.
- `frontend-angular/angular.json`, `architect.build.configurations.production.fileReplacements`
  reemplaza `environment.ts` por `environment.prod.ts` **solo** cuando se
  compila con `ng build` (que usa `production` como configuración por
  defecto, `defaultConfiguration: "production"`) o explícitamente `ng
  build --configuration production`. `ng build --configuration
  development` sigue usando `environment.ts` (verificado: se corrieron
  ambos builds para esta tarea y se confirmó con `grep` sobre los
  archivos `.js` generados que el bundle de producción no contiene
  ningún `localhost:8080` y sí contiene
  `sgb-backend-b058.onrender.com/api`; el bundle de desarrollo contiene
  `localhost:8080` y no contiene la URL de producción).
- Los 6 archivos listados en la sección 7 ahora importan `{ environment
  }` desde `../../environments/environment` (o `../../../environments/environment`
  para `auth.service.ts`, un nivel más profundo) y usan
  `environment.apiUrl` (con el sufijo `/v1` agregado en el código donde
  el valor original ya lo tenía) en vez del literal hardcodeado.

**Limitación real, declarada explícitamente (no es un defecto, es una
propiedad del mecanismo elegido).** `apiUrl` se resuelve **en tiempo de
build**, no en tiempo de ejecución -- queda embebido dentro del bundle
JavaScript ya compilado sobre `dist/frontend-angular/browser/`. Esto
significa que **cualquier cambio futuro de la URL del backend (ej. si el
equipo renombra el servicio en Render, o cambia de proveedor) exige
recompilar y volver a desplegar el frontend** -- no hay forma de
cambiarla por un simple `restart` del servicio o una variable de entorno
inyectada en runtime al contenedor nginx ya construido. Si en el futuro
el equipo necesita cambiar el backend con más frecuencia sin
recompilar, la alternativa sería un `config.json` cargado en runtime
por el frontend (no implementado -- no se justificaba para esta
corrección puntual).

## 9. URLs públicas

- Frontend: <https://biblora-sgb.onrender.com>
- Backend: <https://sgb-backend-b058.onrender.com>

## Referencias

- `render.yaml` (raíz del repo)
- `frontend-angular/Dockerfile`, `frontend-angular/nginx.conf`, `frontend-angular/public/_redirects`
- `frontend-angular/src/environments/environment.ts`, `environment.prod.ts`, `frontend-angular/angular.json`
- `backend-springboot/Dockerfile`
- `docker-compose.yml`, `.env.example`
- `docs/despliegue/RUNBOOK.md`, `docs/despliegue/BACKUP.md`
