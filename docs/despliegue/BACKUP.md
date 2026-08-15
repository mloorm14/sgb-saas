# Respaldo y restauración — SGB-SaaS (Bloque A.4.2)

Esqueleto de la estrategia de respaldo y del procedimiento de
restauración para el despliegue en producción. **Este archivo es un
stub**: no se fabrica ninguna cifra de frecuencia/retención ni ningún
procedimiento de restauración que no se haya ejecutado o decidido
realmente -- se completa en una tarea posterior con información real del
equipo (probablemente ligada al proveedor real de base de datos gestionada,
ver la nota de honestidad sobre Neon/Upstash en
`docs/despliegue/DEPLOYMENT.md`, sección 2).

**Fecha de creación**: 2026-08-15. **Commit base**: `cd25ebe3ad838c94f9b371b65ca39d550021fb1d`.

## 1. Estrategia de respaldo

### Frecuencia

`<PENDIENTE: cada cuánto se respalda la base de datos de producción —
diario, por commit, automático del proveedor gestionado, etc.>`

### Destino

`<PENDIENTE: dónde se almacenan los respaldos — respaldo automático del
proveedor (si aplica según el proveedor real confirmado en
DEPLOYMENT.md), almacenamiento externo adicional, etc.>`

### Retención

`<PENDIENTE: cuántas copias o cuánto tiempo se conservan los respaldos
antes de descartarse>`

### Alcance

`<PENDIENTE: qué se respalda exactamente — solo la base de datos
Postgres (datos de `usuarios`, `libros`, `prestamos`, etc.), o también
el estado de Redis (blacklist de tokens JWT, caché — este último es
regenerable y probablemente no necesita respaldo, ver
docs/adr/ADR-003-jwt-redis.md)>`

## 2. Restauración desde respaldo

`<PENDIENTE: procedimiento paso a paso para restaurar un respaldo —
quién lo ejecuta, con qué comando/interfaz (dashboard del proveedor
gestionado, `pg_restore` manual, etc.), y cómo se verifica que la
restauración fue exitosa (ej. contra qué smoke test o healthcheck) antes
de considerar el sistema recuperado.>`

### Nota sobre el estado local del proyecto (esto sí es real, no un placeholder)

En el entorno de desarrollo local, `docker-compose.yml` monta
`db/init/` (generado por `scripts/build-init-sql.sh` a partir de
`db/schema.sql` + `db/procs/*.sql` + `db/seed.sql`) como script de
inicialización de un volumen Postgres **vacío** -- esto reconstruye el
esquema y los datos de ejemplo desde cero, pero **no es un mecanismo de
respaldo/restauración de datos reales de producción**, es la forma en
que el equipo levanta un entorno local reproducible. No debe confundirse
uno con el otro al completar este documento.

## Referencias

- `docs/despliegue/DEPLOYMENT.md` (proveedor y topología)
- `docs/despliegue/RUNBOOK.md`
- `docker-compose.yml`, `scripts/build-init-sql.sh`, `db/schema.sql`
