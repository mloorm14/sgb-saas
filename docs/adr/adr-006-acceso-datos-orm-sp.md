# ADR-006: Estrategia híbrida de acceso a datos — CRUD elemental vía ORM, el resto vía stored procedures

## Title

CRUD elemental (operaciones de una sola tabla) implementado con Spring
Data JPA; cualquier operación con joins, agregaciones, validación
cruzada multi-tabla o transacción atómica compleja implementada como
procedimiento/función SQL en PostgreSQL.

## Context

La guía de la Tercera Entrega (A.2) exige una estrategia híbrida
explícita: no todo el acceso a datos puede resolverse con un solo
mecanismo. El proyecto ya implementó esta estrategia — 7 objetos SQL en
`db/procs/`, catalogados con su justificación individual en
`docs/basedatos/CATALOGO-SP.md` — pero esa decisión de fondo (por qué
un híbrido, y no uno de los dos extremos) no tenía un ADR propio en
`docs/adr/`; solo existía como catálogo técnico de "qué hace cada
procedimiento", no como registro de la decisión arquitectónica en sí.
Este ADR cierra ese hueco. El detalle de cada uno de los 7 objetos (sus
parámetros, tablas afectadas, y por qué `@Procedure` vs
`@Query(nativeQuery)` en cada caso) sigue viviendo únicamente en
`docs/basedatos/CATALOGO-SP.md` — no se duplica aquí.

## Decision

Se adopta una estrategia híbrida: **CRUD elemental sobre una sola tabla
va en Spring Data JPA** (métodos derivados o `JpaRepository` estándar);
**cualquier operación con joins, agregaciones, validación cruzada
multi-tabla o transacción atómica que involucre más de una tabla
relacionada va en un procedimiento o función SQL** invocado desde
Spring Data (`@Procedure`/`@NamedStoredProcedureQuery` o
`@Query(nativeQuery = true)`, según el caso — ver
`docs/basedatos/CATALOGO-SP.md` para el criterio de cuál mecanismo usar).

## Alternativas consideradas

- **ORM puro para todo (incluida la lógica de préstamos/multas):**
  descartado por dos motivos. Primero, no cumple el requisito explícito
  A.2 de la guía, que exige stored procedures/funciones para el
  subconjunto de operaciones complejas — no es una preferencia de estilo,
  es un criterio de evaluación de esta entrega. Segundo, expresar en
  Java lógica como la de `sp_registrar_devolucion` (hasta 4 tablas
  leídas/escritas en una transacción condicionada por reglas de negocio:
  cálculo de atraso, lectura de configuración, generación de multa,
  bloqueo de usuario) exigiría múltiples round-trips no atómicos a la
  base de datos desde el código Java, o un manejo manual de
  transacciones más frágil que confiar en la atomicidad implícita de una
  función PL/pgSQL — un trade-off real de rendimiento (más round-trips
  de red) y de mantenibilidad (la lógica de integridad queda dispersa en
  el código Java en vez de garantizada por el motor).
- **Stored procedures puros para todo (incluido el CRUD elemental de
  `libros`, `usuarios`, etc.):** descartado por sobre-ingeniería. Un
  `INSERT`/`UPDATE`/`SELECT` de una sola tabla sin lógica cruzada no gana
  nada envolviéndolo en una función SQL — sí pierde: dificulta el testing
  unitario del código Java (un repositorio JPA estándar se puede probar
  con una base de datos en memoria o mocks de forma directa; una llamada
  a un procedimiento exige siempre una base de datos real o
  Testcontainers), y añade un archivo `.sql` a mantener por cada
  operación trivial que Spring Data ya resuelve con un método derivado
  (`findByTituloContaining`, etc.) sin una sola línea de SQL manual.
- **Híbrido (elegido):** cada mecanismo se usa donde tiene ventaja real
  — ORM para lo simple (rapidez de desarrollo, testability), SQL nativo
  para lo complejo (atomicidad garantizada por el motor, expresividad de
  joins/agregaciones que Spring Data no puede derivar automáticamente).

## Status

Aceptado e implementado. Los 7 objetos están aplicados y probados
manualmente contra una instancia real de PostgreSQL 16 (ver la sección
"Validación" de `docs/basedatos/CATALOGO-SP.md` para el detalle completo
de casos cubiertos). El seguimiento que quedaba abierto —verificación en
runtime de `sp_registrar_devolucion`, `sp_pagar_multa` y
`sp_anular_multa`, los 3 procedimientos con múltiples parámetros OUT—
ya se cerró: la primera ejecución real confirmó el fallo con
`@NamedStoredProcedureQuery`; tras migrar a
`@Query(nativeQuery = true)` los 6 escenarios de
`PrestamoMultaProcedureIntegrationTest` pasan en verde. No cambia la
decisión del ADR (SQL nativo para operaciones multi-tabla), solo el
mecanismo de invocación de esos 5 métodos. Ver evidencia completa en
`docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md`.

## Consequences

**Positivas:**

- Cumple el requisito A.2 de la guía de forma verificable: el criterio
  de "cuándo usar cada mecanismo" está documentado y aplicado
  consistentemente en los 7 objetos existentes.
- La atomicidad de operaciones multi-tabla (ej. decrementar stock +
  insertar préstamo en `sp_crear_prestamo`) queda garantizada por el
  motor (`RAISE EXCEPTION` revierte toda la transacción de la
  invocación), no por disciplina de código Java.
- El CRUD elemental conserva la velocidad de desarrollo y testability de
  Spring Data JPA donde no hay lógica cruzada que justifique SQL manual.

**Negativas:**

- Dos mecanismos de acceso a datos conviven en el mismo proyecto: un
  desarrollador nuevo (o Cajas, que construye la capa de servicio de
  Préstamos) debe entender el criterio de cuándo usar cada uno en vez de
  un único patrón uniforme — mitigado documentando el criterio
  explícitamente aquí y en `docs/basedatos/CATALOGO-SP.md`.
- Los procedimientos con múltiples parámetros OUT vía
  `@NamedStoredProcedureQuery` eran un área frágil conocida de la
  combinación Hibernate/pgjdbc — confirmado en la práctica (fallaban en
  runtime) y resuelto migrando a `@Query(nativeQuery = true)`, ver
  Status arriba.

## Actualización (2026-08-16): mecanismo de invocación uniforme vía `@Query(nativeQuery = true)`

Esta sección actualiza el ADR sin reescribir la decisión original: el fondo
de la decisión (estrategia híbrida: CRUD elemental en Spring Data JPA,
lógica compleja multi-tabla en objetos SQL de `db/procs/`) **no cambia**. Lo
que cambió es el mecanismo de invocación concreto desde Java hacia esos
objetos.

### Qué se asumió originalmente

La decisión original asumía que las funciones con efectos secundarios se
invocarían con la API de stored procedures de JPA 2.1 (`@Procedure` sobre
método de repositorio, o `@NamedStoredProcedureQuery` sobre entidad) — el
mecanismo literal que nombra el requisito A.2.1 de la guía de la Tercera
Entrega — y que solo las funciones tabulares (`RETURNS TABLE`) usarían
`@Query(nativeQuery = true)`.

### Qué pasó en la práctica

La verificación en runtime del primer caso real (evidencia en
`docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md`)
confirmó que **todos** los intentos con `@Procedure`/`@NamedStoredProcedureQuery`
fallaban con el mismo error: Hibernate genera la llamada dentro del escape
JDBC `{call ...}` con la sintaxis de parámetros nombrados de PostgreSQL
(`nombre => valor`), que el driver `pgjdbc` rechaza ahí — bug conocido de
Hibernate 6.2+/7.x, referenciado como `spring-projects/spring-data-jpa#3393`,
sin fix oficial a la fecha. El fallo no era exclusivo de los parámetros OUT:
afectó igualmente al retorno escalar de `sp_crear_prestamo` y a la
invocación de `sp_expirar_reservaciones_vencidas` (que además Postgres
rechaza como `call ...` porque el objeto es `FUNCTION`, no `PROCEDURE`
nativo).

### Decisión de esta actualización

Los 9 objetos SQL de `db/procs/` (ver el catálogo actualizado en
`docs/basedatos/CATALOGO-SP.md`) se invocan hoy de forma uniforme vía
`@Query(nativeQuery = true)` con parámetros nombrados `@Param` — incluidas
las 5 funciones con efectos secundarios que el ADR original pensaba invocar
con `@Procedure`. Las 4 funciones tabulares usaban ese mecanismo desde el
inicio (JPA 2.1 no expone `RETURNS TABLE`, ver CATALOGO-SP.md).

Esto es una **desviación conocida y deliberada** del mecanismo literal que
nombra el requisito A.2.1 de la guía ("`@Procedure` sobre método de
repositorio Spring Data o `@NamedStoredProcedureQuery` sobre entidad"). Se
reporta explícitamente en lugar de ocultarlo, porque el criterio de
evaluación A.2.1/A.2.2 revisa este punto.

### Justificación honesta

La intención de fondo del requisito (A.2.3) es impedir SQL dinámico o
construido por concatenación de entrada de usuario. Eso se cumple: los
parámetros se bindean por nombre con `@Param` y se transportan como
parámetros vinculados de `PreparedStatement`, idéntico a lo que hace
`@Procedure` internamente; no existe `EXECUTE`, `sp_executesql` ni
concatenación de strings en ningún acceso a datos del backend. El
cumplimiento de parametrización se verifica además con la regla estática
`SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` (Find Security Bugs), añadida al
build.

El mecanismo literal de A.2.1 se volvió técnicamente inviable para esta
combinación concreta de ORM (Hibernate 6.2+/7.x) + driver (`pgjdbc`) + motor
(PostgreSQL): aplicar la API de stored procedures de JPA 2.1 produce SQL que
el driver no puede ejecutar. Elegir el mecanismo funcionalmente equivalente
(`@Query` nativa parametrizada) en lugar de romper el runtime es la opción
que preserva la intención del requisito (seguridad de parámetros, sin SQL
dinámico) y mantiene la estrategia híbrida exigida (los objetos SQL complejos
siguen existiendo y ejecutándose).

### Impacto

- No cambió ninguna decisión funcional del ADR: la estrategia híbrida y la
  existencia de los objetos SQL se mantienen.
- El código Java ya estaba correcto y verificado (tests de integración en
  verde); este ADR y el catálogo se alinean con la realidad del código, que
  no se modificó.
- Quedaba pendiente en el código el comentario "pendiente de actualizar
  ADR-013" presente en `PrestamoProcedureRepository.java`; con esta
  actualización, esa deuda documental queda saldada.

## Referencias

- [[adr-011-gestor-base-datos]] (PostgreSQL como motor que hace posible PL/pgSQL)
- `docs/basedatos/CATALOGO-SP.md` (catálogo completo de los 9 procedimientos/funciones y su mecanismo de invocación)
- `db/procs/` (fuente SQL de los 9 objetos)
- `docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md` (evidencia del fallo de `@Procedure`/`@NamedStoredProcedureQuery` y de la migración a `@Query(nativeQuery)`)