# Despliegue de producción — SGB-SaaS

> Documento base del despliegue oficial del sistema para la Entrega Final
> (guía, Bloque A.4 / criterio P5 de la rúbrica). Complementos operativos:
> [RUNBOOK.md](RUNBOOK.md) (operación de día a día) y [BACKUP.md](BACKUP.md)
> (respaldo y restauración).

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
> la Actualización fechada en `docs/adr/adr-012-estrategia-despliegue.md`.

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
   rama `conf-produccion`, que contiene el `Dockerfile` multi-stage del
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

1. **New → Static Site**: mismo repo, rama `conf-produccion`.
2. Build command: `npm run build --prefix frontend-angular` (equivale al
   `<build>` del entorno de CI), Publish directory:
   `frontend-angular/dist/`.
3. El bracket del bundle ya apunta a la URL pública del backend
   (`https://sgb-backend-b058.onrender.com/api/v1`); si el host del Web
   Service difiere, editar los `environment`/URLs de servicio del módulo
   correspondiente y reconstruir.
4. **Create Static Site** → Render sirve el sitio con HTTPS automático y
   sin dormir.

### 5.5 Verificación integral (punto a punto)

1. `curl https://<backend>.onrender.com/actuator/health` → `UP`.
2. Abrir `https://<frontend>.onrender.com` en el navegador: carga la SPA.
3. `POST https://<backend>.onrender.com/api/v1/auth/login` con credenciales
   válidas → 200 con `accessToken`; repetir con password incorrecto 5
   veces y confirmar `429` (rate limiting vía Upstash).
4. En los dashboards de Neon y Upstash confirmar conexiones/uso en vivo
   (prueba de que el backend llega a ambos por URL pública TLS).
5. Si el primer acceso tarda ~60 s, es el *cold start* del plan Free
   (comportamiento esperado, §2.1).

## 6. Referencias

- [RUNBOOK.md](RUNBOOK.md) — operación, rotación de secretos y redeploys.
- [BACKUP.md](BACKUP.md) — respaldo Neon, retención mínima y prueba de
  restauración.
- [ADR-012](docs/adr/adr-012-estrategia-despliegue.md) — decisión y
  Actualización a esta arquitectura.
- Documentación oficial de límites verificada: `render.com/docs/free`,
  `neon.com/docs/introduction/plans`, `upstash.com/pricing/redis`
  (agosto 2026).