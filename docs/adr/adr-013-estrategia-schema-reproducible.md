# ADR-013: Estrategia de esquema reproducible (Flyway + db/schema.sql y db/seed.sql)

## Title

Estrategia de esquema reproducible: Flyway para evolución incremental,
`db/schema.sql` + `db/seed.sql` como snapshot consolidado para
reproducibilidad desde cero.

## Context

El proyecto usa Flyway (`database/migrations/`, montado en el classpath del
backend como `db/migration`) como mecanismo de versionado incremental del
esquema desde la Entrega 1B: cada cambio estructural se agrega como un
archivo `V{n}__descripcion.sql` nuevo, nunca se modifica uno ya aplicado, y
Spring Boot lo ejecuta automáticamente al arrancar (`spring.flyway.enabled=true`).
Este mecanismo seguirá siendo la fuente de verdad para *cómo llegó* el
esquema a su estado actual, y es el que se usa en cualquier entorno con
datos ya existentes (staging, producción).

La guía de la Tercera Entrega (Bloque B.1 — Reproducibilidad automática)
exige que un evaluador pueda levantar el sistema **desde cero** con un solo
comando (`make up` / `docker compose up --build`) y obtener una base de
datos ya poblada con el esquema completo y datos de ejemplo, sin tener que
ejecutar manualmente Java/Maven ni entender el historial de migraciones.
Ejecutar 24 tablas divididas en varias migraciones incrementales sería
tedioso de auditar visualmente para ese propósito, y mezclar "esquema
completo de referencia" con "historial de cómo se construyó" en el mismo
mecanismo dificulta ambos objetivos a la vez.

## Decision

Se mantienen **dos mecanismos distintos con propósitos distintos**:

1. **Flyway (`database/migrations/V1__...`, `V2__...`, ...)**: sigue siendo
   el mecanismo real de versionado incremental del esquema. Es la única
   fuente de verdad para bases de datos que ya tienen datos (o van a
   tenerlos) y deben evolucionar sin perder información. Todo cambio futuro
   de esquema se hace primero aquí.
2. **`db/schema.sql` + `db/procs/*.sql` + `db/seed.sql`**: un snapshot
   consolidado, de solo lectura conceptual, que representa el estado
   *objetivo* del esquema (26 tablas), los procedimientos/funciones del
   módulo de Préstamos (ver `docs/basedatos/CATALOGO-SP.md`) y datos
   mínimos de ejemplo (catálogos, usuario administrador, libros de
   muestra). Como PostgreSQL no recorre subdirectorios de
   `docker-entrypoint-initdb.d/`, estos tres orígenes se concatenan con
   `scripts/build-init-sql.sh` en un único `db/init/01-consolidado.sql`
   (carpeta `db/init/` gitignored, regenerada en cada `make up`), y es
   **esa carpeta** la que se monta en `docker-entrypoint-initdb.d/` del
   contenedor de Postgres — no el directorio `db/` completo. Así `make up`
   deja el sistema listo para usarse y evaluarse sin pasos manuales
   adicionales. Este snapshot
   **no sustituye** a Flyway: se regenera manualmente cada vez que el
   esquema objetivo cambia de forma significativa, y no se ejecuta nunca
   contra una base de datos que ya tenga datos reales o migraciones de
   Flyway aplicadas (`docker-entrypoint-initdb.d/` solo corre en la
   primera inicialización de un volumen de datos vacío).

## Status

Aceptado. Pendiente de aplicación: `db/schema.sql` y `db/seed.sql` fueron
generados a partir de la fusión de `database/migrations/V1__schema_inicial.sql`
(Entrega 1B) y el esquema de 24 tablas del módulo de Administración de BD, y
están sujetos a revisión antes de ejecutarse contra cualquier contenedor
(discrepancias de columnas entre ambas fuentes reportadas por separado).

## Consequences

**Positivas:**

- Un evaluador o un nuevo integrante del equipo puede tener un entorno
  completo y poblado con un solo `make up`, sin necesitar conocer el
  historial de migraciones ni ejecutar comandos adicionales.
- El esquema "objetivo" queda documentado en un único archivo legible de
  punta a punta, útil para revisiones de diseño de base de datos y para la
  sustentación del PFC.
- Flyway conserva su rol correcto: control de cambios incremental y seguro
  para entornos con datos reales, sin que el snapshot de conveniencia
  interfiera con él.

**Negativas:**

- Existe duplicación de intención entre `database/migrations/` y
  `db/schema.sql`: cada cambio de esquema debe reflejarse en ambos lugares
  (una migración `V{n}__` nueva y, en algún punto, una actualización del
  snapshot), lo que introduce riesgo de que se desincronicen si no se
  disciplina el proceso.
- `db/schema.sql` y `db/seed.sql` solo aplican en una inicialización desde
  volumen vacío; si se ejecutan por error contra una base de datos con
  datos existentes, `docker-entrypoint-initdb.d/` simplemente no los
  correrá (comportamiento estándar de la imagen oficial de Postgres), pero
  esto puede confundir a quien no conozca esa semántica.
- Requiere disciplina del equipo para no tratar el snapshot como "la"
  fuente de verdad y terminar editándolo directamente en vez de crear una
  migración Flyway nueva.
