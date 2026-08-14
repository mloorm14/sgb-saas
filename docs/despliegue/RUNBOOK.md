# RUNBOOK — Operación de producción SGB-SaaS

Guía operativa de día a día sobre la arquitectura desplegada en
Render + Neon + Upstash. Despliegue de referencia: [DEPLOYMENT.md](DEPLOYMENT.md).

## 1. Arranque / apagado

### 1.1 No existe un "apagado" tradicional

Render no tiene un comando `stop` como una VM: los servicios se
**suspenden** (dejan de ejecutarse y de consumir horas del plan) o
**reanudan** desde el dashboard, y el plan Free además **duerme solo** por
inactividad:

- **Backend (Web Service Free):** duerme automáticamente tras **15
  minutos sin tráfico**. No requiere ninguna acción: la siguiente petición
  HTTP lo reactiva en ~30–60 s (*cold start*). Un "apagado" voluntario
  adicional (p. ej. para no gastar horas del mes antes de una demo) se
  hace con **Suspend**.
- **Frontend (Static Site):** se sirve desde CDN y **no duerme ni consume
  horas**; no requiere arranque/apagado. Si igual se quiere desactivar
  temporalmente, aplicar **Suspend** (el sitio deja de responder).

### 1.2 Suspender un servicio (apagado voluntario)

1. Dashboard de Render → abrir el servicio (Web Service o Static Site).
2. Menú del servicio (los `...` / acciones) → **Suspend** (también
   disponible en lote: tildar los servicios en el dashboard → **Suspend**).
3. Estado pasa a *Suspended*; el backend deja de consumir horas de
   instancia y el sitio/frontend queda inaccesible.

### 1.3 Reanudar (arranque)

1. Abrir el servicio → **Resume** (o lote: tildar → **Resume**).
2. Para el Web Service free: tras *Resume* la primera petición puede
   tardar ~60 s (cold start); verificarlo con
   `curl https://sgb-backend-b058.onrender.com/actuator/health`.

> Nota: **Neon y Upstash no se apagan** — están siempre disponibles y su
> consumo con el backend suspendido es ~0 (Neon escala a cero a los 5 min
> de inactividad; Upstash cobra solo comandos). No hace falta suspenderlos
> nunca salvo decisión de desmantelamiento (ver [BACKUP.md](BACKUP.md)).

## 2. Rotación de secretos

Objetivo: reemplazar un secreto comprometido o por política sin downtime
prolongado. En Render **guardar un cambio de variable no destruye la
instancia**: la nueva configuración se aplica en el siguiente *deploy*,
que se dispara manualmente cuando el equipo lo decide (ver §3). Para los
secretos críticos el patrón es: **regenerar en el proveedor → pegar el
nuevo valor en Render → redeploy manual → verificar**.

### 2.1 JWT_SECRET

```bash
openssl rand -hex 32
```

1. Generar con el comando de arriba (64 caracteres hex = 256 bits).
2. Render → backend → **Environment** → editar `JWT_SECRET` → Save.
3. **Manual Deploy → Deploy latest commit** (§3). Al reiniciar, todos los
   `accessToken`/`refreshToken` emitidos con la clave anterior dejan de
   validarse: **los usuarios deben volver a iniciar sesión**. La blacklist
   en Upstash sigue válida (referencia por `jti`, no por clave).
4. Verificar: login nuevo en el frontend desplegado + health check.

### 2.2 Password de Neon

1. Neon dashboard → proyecto → **Roles** (o Settings → Roles).
2. En el rol usado por el backend (`neondb_owner`): **Reset password**
   (genera una nueva contraseña; se copia del panel).
3. Render → backend → **Environment** → `DB_PASSWORD` = nuevo valor → Save
   → **Manual Deploy** (§3).
4. Verificar: `curl .../actuator/health` debe mostrar el componente `db`
   en `UP`; revisar los logs del deploy por fallos de autenticación.

### 2.3 Token de Upstash

1. Upstash dashboard → base de datos → pestaña **Details** → **REST
   Token**: botón **Regenerate** (el token viejo queda invalidado de
   inmediato).
2. Render → backend → **Environment** → `REDIS_PASSWORD` = token nuevo →
   Save → **Manual Deploy** (§3).
3. Verificar: health (`redis` en `UP`) y una acción real de login con
   contraseña fallida 5 veces (esperar `429`, prueba de que el rate
   limiting escribe en Upstash).

### 2.4 Ventana de downtime esperada

`Deploy latest commit` con la nueva variable no causa downtime de build
(la imagen ya está compilada y cacheada; Render la vuelve a construir si
hay cambios de código, pero una rotación de secretos no toca código): la
ventana real es la del reinicio del contenedor (~10–60 s en free).
Además, `SPRING_FLYWAY_LOCATIONS` y los datos en Neon no se ven afectados:
**ninguna rotación de secretos toca datos**.

## 3. Rotación de contenedores (redeploy manual)

Se usa cuando hay que **aplicar una imagen nueva** sin esperar un push al
repo: cambio de dependencias, versión base (JDK/OS), o simplemente
reproducir el estado del último commit.

1. Dashboard de Render → backend (Web Service).
2. **Manual Deploy → Deploy latest commit** (despliega el HEAD de la
   rama configurada en el servicio — actualmente
   `feature/despliegue-produccion`).
3. Render reconstruye la imagen (consume minutos de build del free tier,
   500 min/mes) y la publica con zero-downtime; el estado del deploy se ve
   en la pestaña **Events**.
4. Verificar `/actuator/health` y una operación de login.

Alternativas: si solo se quiere re-ejecutar sin cambios de imagen,
**Restart** (botón del servicio) reinicia la instancia actual; **Rollback**
vuelve a un deploy previo listado en **Events** (útil si un redeploy
rompe algo — p. ej. una migración Flyway fallida).

## 4. Restauración (recuperación ante incidente)

En esta arquitectura **no hay Postgres autogestionado ni dump manual**:
el mecanismo principal de restauración es el **branching / point-in-time
restore de Neon**, que funciona sobre el historial que Neon mantiene de la
rama raíz (ver [BACKUP.md](BACKUP.md) para los límites del plan Free y el
procedimiento de prueba).

Flujo ante borrado/corrupción de datos:

1. Neon dashboard → proyecto → **Branches** → **Create branch** desde la
   rama raíz y el punto en el tiempo deseado (dentro de la ventana de
   historial vigente, 6 h en Free; la rama nueva queda en estado de solo
   lectura o se puede conectar un cómputo para inspeccionarla).
2. Verificar en la rama restaurada los datos afectados (consulta directa
   o con psql a su connection string).
3. Para volver al punto bueno de forma estable: **Restore** de la rama
   raíz a ese timestamp (rewind) — los pasos exactos están en la
   documentación de Neon (`neon.com/docs/guides/branch-restore`) — o, si
   el equipo prefiere mínimo riesgo, **apuntar el backend a la rama
   restaurada** cambiando `DB_URL` en Render y redeployando (§2.4).
4. Nunca restaurar encima de la rama principal sin haber hecho antes una
   copia lógica de emergencia (`pg_dump` contra la URL de Neon) del estado
   dañado, por si se necesita auditoría posterior.

> No existe script de restauración manual en el repositorio porque el
> plan Free de Neon no expone un backup "descargable" como producto; la
> recuperación se apoya 100 % en la infraestructura gestionada de Neon.

## 5. Referencias

- [DEPLOYMENT.md](DEPLOYMENT.md) — topología, límites y despliegue desde cero.
- [BACKUP.md](BACKUP.md) — retención, fechas críticas y prueba de restauración.
- `docs/adr/adr-012-estrategia-despliegue.md` — decisión arquitectónica.
- Documentación oficial: `render.com/docs/free`, `render.com/docs/deploys`,
  `neon.com/docs/guides/branch-restore`, `upstash.com/docs`.