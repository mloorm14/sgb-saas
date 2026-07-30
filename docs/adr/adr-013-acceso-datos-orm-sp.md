# ADR-013: Estrategia híbrida de acceso a datos — CRUD elemental vía ORM, el resto vía stored procedures

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

## Referencias

- [[adr-011-gestor-base-datos]] (PostgreSQL como motor que hace posible PL/pgSQL)
- `docs/basedatos/CATALOGO-SP.md` (catálogo completo de los 7 procedimientos/funciones, criterio `@Procedure` vs `@Query(nativeQuery)`)
- `db/procs/` (fuente SQL de los 7 objetos)