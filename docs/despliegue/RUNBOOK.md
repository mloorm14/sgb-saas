# Runbook operativo — SGB-SaaS (Bloque A.4.2)

Esqueleto de procedimientos operativos para el despliegue en producción
(Render). **Este archivo es un stub**: solo trae la estructura de
secciones exigida, no procedimientos completos -- ninguna sección de
producción se completa con información que no esté confirmada, para no
fabricar un procedimiento que nadie ejecutó todavía. Se completa en una
tarea posterior con información real del equipo.

**Fecha de creación**: 2026-08-15. **Commit base**: `cd25ebe3ad838c94f9b371b65ca39d550021fb1d`.

## 1. Arranque

### Entorno local (Docker Compose) — esto sí está confirmado y en uso real

```bash
make up
```

Regenera `db/init/01-consolidado.sql` (`scripts/build-init-sql.sh`) y
levanta Postgres, Redis, backend y frontend vía `docker compose up -d
--build`, esperando a que los 4 healthchecks definidos en
`docker-compose.yml` reporten `healthy` (timeout ~60s). Ver
`docker-compose.yml` y el propio `Makefile` para el detalle exacto.

### Producción (Render)

`<PENDIENTE: procedimiento de arranque/reinicio de los servicios en el
dashboard de Render (sgb-backend, biblora-sgb) — orden de arranque si
aplica, tiempo esperado hasta health check en verde, a quién del equipo
le corresponde ejecutarlo>`.

## 2. Apagado

### Entorno local

```bash
make down
```

Detiene y elimina los contenedores, **conservando** el volumen `pgdata`
(no se pierden datos entre reinicios locales).

### Producción (Render)

`<PENDIENTE: procedimiento de apagado/pausa de los servicios en Render
— si el equipo pausa manualmente los servicios fuera de la ventana de
evaluación del tribunal, o si se dejan corriendo permanentemente en el
free tier>`.

## 3. Rotación de secretos

`<PENDIENTE: procedimiento y frecuencia para rotar JWT_SECRET,
DB_PASSWORD, GEMINI_API_KEY, credenciales SMTP en producción — incluye
cómo invalidar sesiones/tokens ya emitidos con el secreto anterior
(ver ADR-003, blacklist de tokens en Redis) sin dejar el sistema
inoperable durante la rotación. No hay evidencia en el repositorio de
que esto se haya ejecutado nunca en producción todavía.>`

## 4. Rotación de contenedores

`<PENDIENTE: política de actualización de las imágenes base pinadas por
digest (ver docs/DIGESTS-LOG.md — eclipse-temurin, postgres, redis,
nginx, node, todas fijadas por sha256 al 2026-07-22) — con qué
frecuencia se re-pinan a una versión más reciente, quién lo decide, y
el procedimiento de despliegue de la imagen nueva en Render (rebuild vía
render.yaml / Blueprint sync, o manual).>`

## Referencias

- `docs/despliegue/DEPLOYMENT.md` (topología y procedimiento de despliegue inicial)
- `docs/despliegue/BACKUP.md`
- `Makefile`, `docker-compose.yml`
- `docs/DIGESTS-LOG.md`
- `docs/adr/ADR-003-jwt-redis.md` (blacklist de tokens JWT en Redis)
