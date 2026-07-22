# ADR-011: PostgreSQL 16 como gestor de base de datos

## Title

Selección de PostgreSQL 16 sobre MySQL 8 y MongoDB como motor de base de
datos de SGB-SaaS.

## Context

El sistema requiere persistencia para un dominio con relaciones estrictas
(usuarios, roles, libros, préstamos, reservas, multas, auditoría — 26
tablas en `db/schema.sql`), la estrategia híbrida CRUD-ORM + stored
procedures obligatoria para esta entrega (ver
`docs/basedatos/CATALOGO-SP.md` y `adr-013-acceso-datos-orm-sp.md`), y
aislamiento de datos por rol a nivel de fila diseñado para los propios
lectores del sistema (`db/roles-privilegios.sql`).

`ADR-001-tecnologia.md` ya asume PostgreSQL como elegido y
`adr-006-estrategia-schema-reproducible.md` asume el esquema ya definido
sobre Postgres; ninguno de los dos documenta la comparación contra
alternativas de gestor de base de datos. Este ADR llena ese hueco.

## Decision

Se elige **PostgreSQL 16** como único gestor de base de datos del
sistema.

## Alternativas consideradas

- **MySQL 8:** descartado. Carece de **Row Level Security (RLS) nativo**
  a nivel de motor — el proyecto ya diseñó e implementó aislamiento de
  datos por fila para que un lector solo pueda ver sus propios
  préstamos/reservas/multas/favoritos/sugerencias
  (`db/roles-privilegios.sql`, sección 7:
  `ALTER TABLE ... ENABLE/FORCE ROW LEVEL SECURITY` +
  `CREATE POLICY ... USING (usuario_id = current_setting('app.current_user_id')::bigint)`
  sobre `favoritos`, `reservaciones`, `prestamos`, `multas` y
  `sugerencias_adquisicion`). Replicar esto en MySQL exigiría mover el
  filtrado por usuario a la capa de aplicación (Java), perdiendo la
  garantía de que **ninguna consulta, ni siquiera una mal escrita en un
  endpoint nuevo, pueda filtrar datos de otro usuario** — la ventaja
  central de RLS es que la restricción vive en el motor, no en cada
  punto de acceso.
- **MongoDB:** descartado por dos motivos independientes, cualquiera de
  los dos suficiente por sí solo:
  1. **No tiene un equivalente real a PL/pgSQL.** La guía de esta entrega
     exige de forma obligatoria (A.2) una estrategia híbrida ORM +
     procedimientos/funciones SQL para operaciones con joins,
     agregaciones o transacciones atómicas multi-colección (ver los 7
     objetos de `docs/basedatos/CATALOGO-SP.md`, ej. `sp_registrar_devolucion`
     que toca hasta 4 tablas en una sola transacción). MongoDB no ofrece
     un mecanismo de procedimientos almacenados server-side comparable;
     forzar esa lógica al lado del driver (Node/Java) incumpliría
     directamente el requisito A.2 de la guía, no solo sería subóptimo.
  2. **El dominio es intrínsecamente relacional.** Préstamos, reservas y
     multas tienen integridad referencial estricta entre sí (un
     préstamo referencia un usuario y un libro que deben existir; una
     multa referencia un préstamo; el stock de un libro se decrementa
     atómicamente al crear un préstamo). Modelar esto en documentos
     exigiría o bien duplicar datos entre colecciones (con el riesgo de
     inconsistencia que eso implica sin transacciones ACID multi-documento
     maduras y sin FKs que el motor valide) o bien referencias manuales
     sin garantía de integridad a nivel de motor — exactamente lo que
     los `FOREIGN KEY`/`CHECK` de `db/schema.sql` garantizan hoy de forma
     nativa.
- **PostgreSQL 16 (elegido):** RLS nativo para el aislamiento por rol ya
  diseñado, PL/pgSQL maduro para los 7 objetos de
  `docs/basedatos/CATALOGO-SP.md`, integridad referencial estricta con
  `FOREIGN KEY`/`CHECK` sobre un dominio genuinamente relacional, y
  soporte JDBC de primera clase para Spring Data JPA + `@Procedure`/
  `@Query(nativeQuery)`.

## Status

Aceptado e implementado. Verificado en vivo: las 26 tablas, los 7
procedimientos/funciones y las políticas RLS de `db/roles-privilegios.sql`
corren contra un contenedor real `postgres:16-alpine` (`sgb_postgres`, ver
`docker-compose.yml` y `docs/DIGESTS-LOG.md` para el digest exacto
pinado).

## Consequences

**Positivas:**

- El aislamiento de datos por lector (RLS) queda garantizado por el
  motor, no por disciplina de código en cada endpoint nuevo que se
  agregue a futuro.
- Los 7 procedimientos/funciones de `docs/basedatos/CATALOGO-SP.md`
  tienen un lenguaje maduro (PL/pgSQL) con manejo de excepciones
  (`RAISE EXCEPTION` con SQLSTATE personalizado) y transaccionalidad
  implícita por invocación.
- Ecosistema Docker de primera clase (imagen oficial `postgres:16-alpine`,
  pinada por digest) y soporte nativo de Flyway para versionado de
  esquema (`adr-006-estrategia-schema-reproducible.md`).

**Negativas:**

- Escalado horizontal de escritura (sharding) no es tan directo como en
  motores diseñados para eso desde el origen (ej. MongoDB con sharding
  nativo) — se acepta porque el volumen de una biblioteca universitaria
  no se acerca a ese escenario (ver prioridad "Media" de Eficiencia de
  desempeño en `docs/arquitectura/ISO25010.md`).
- RLS añade una capa de configuración por sesión (`app.current_user_id`
  vía `SET`) que el backend debe fijar correctamente en cada conexión del
  pool — documentado en el comentario extenso de
  `db/roles-privilegios.sql` (sección "FIX: nota extensa sobre RLS +
  connection pooling") como un punto de atención real, no trivial.

## Referencias

- [[ADR-001-tecnologia]] (elección de la pila principal; remite aquí para el detalle)
- [[adr-013-acceso-datos-orm-sp]] (estrategia CRUD-ORM + stored procedures que PostgreSQL hace posible)
- [[adr-006-estrategia-schema-reproducible]] (versionado de esquema sobre este motor)
- `db/roles-privilegios.sql` (sección 7, Row Level Security)
- `docs/basedatos/CATALOGO-SP.md` (catálogo de los 7 procedimientos/funciones)
