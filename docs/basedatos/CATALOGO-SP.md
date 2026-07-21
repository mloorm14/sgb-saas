# Catálogo de procedimientos almacenados y funciones — módulo Préstamos

Inventario de los objetos SQL versionados en `db/procs/`, requeridos por la
estrategia híbrida obligatoria de la guía de la Tercera Entrega (A.2):
operaciones CRUD elementales van en JPA/Spring Data; cualquier operación con
joins, agregaciones, validación cruzada multi-tabla o transacción atómica
compleja va en un procedimiento almacenado o función SQL.

## Nota de diseño: FUNCTION en todos los casos, no PROCEDURE nativo

Los 7 objetos están implementados como `CREATE OR REPLACE FUNCTION` (no
`CREATE PROCEDURE`), incluidos los que tienen efectos secundarios (INSERT/
UPDATE). Razón: Spring Data JPA (`@Procedure`, `@NamedStoredProcedureQuery`)
y el driver JDBC de PostgreSQL tienen soporte más amplio y predecible para
funciones que para el objeto nativo `PROCEDURE` (agregado recién en
PostgreSQL 11, con semántica de invocación `CALL` distinta a la que asume la
API de stored procedures de JPA 2.1). Usar `FUNCTION` de forma uniforme
evita mezclar dos mecanismos de invocación distintos en el mismo módulo. La
atomicidad requerida por la guía se cumple igual: cada función corre en la
transacción implícita de su propia invocación — cualquier `RAISE EXCEPTION`
revierte todos los cambios hechos dentro de esa llamada.

## Nota de diseño: por qué 5 usan `@Procedure`/`@NamedStoredProcedureQuery` y 2 usan `@Query(nativeQuery = true)`

Las 5 funciones con efectos secundarios (`sp_crear_prestamo`,
`sp_registrar_devolucion`, `sp_pagar_multa`, `sp_anular_multa`,
`sp_expirar_reservaciones_vencidas`) se invocan desde Spring Data vía
`@Procedure` (directo o referenciando un `@NamedStoredProcedureQuery`),
siguiendo el contrato de stored procedures de Jakarta Persistence 2.1 que
exige la guía.

Las 2 funciones de solo lectura que retornan `TABLE` (varias filas —
`fn_listar_prestamos_activos_por_usuario`, `fn_reporte_libros_mas_prestados`)
usan en cambio `@Query(nativeQuery = true)`. Motivo técnico: la API de
stored procedures de JPA 2.1 está construida sobre JDBC `CallableStatement`,
que en PostgreSQL solo expone resultados vía un valor escalar/OUT o un
parámetro `REF_CURSOR` — no vía `RETURNS TABLE`/`SETOF` invocado como
función. Forzar estas dos a devolver un `refcursor` para encajar en
`@NamedStoredProcedureQuery` tendría dos costos reales sin ninguna ganancia:
(1) dejarían de poder invocarse directo como `SELECT * FROM fn_...(...)`
desde `psql`/Postman/otras herramientas de depuración, y (2) complicaría la
definición SQL de la función solo para satisfacer una anotación. `@Query`
nativa con parámetros nombrados (`:p_usuario_id`, `:p_limite`, ...) es el
patrón estándar documentado de Spring Data para funciones PostgreSQL que
retornan tabla, y **cumple igual la regla A.2.3** (invocación parametrizada
nombrada, sin `EXECUTE`/SQL dinámico, sin concatenación de entrada de
usuario en la sentencia): los parámetros se bindean por nombre exactamente
igual que en un `@Procedure`, la única diferencia es el mecanismo JDBC
usado por Hibernate para transportar la llamada. No es una inconsistencia
del diseño: es la solución correcta para el subconjunto de casos donde el
contrato de JPA 2.1 no tiene forma de representar un resultado tabular de
PostgreSQL.

### Addendum — confirmación explícita (Postgres `RETURNS TABLE` vs JPA 2.1)

Nota adicional a la sección anterior, para que quede como respuesta directa
si en la sustentación preguntan por qué no las 7 funciones usan el mismo
mecanismo: **JPA 2.1 no tiene una forma estándar de exponer un
`RETURNS TABLE`/`SETOF` de PostgreSQL** (múltiples filas) a través de
`@Procedure`/`@NamedStoredProcedureQuery`. La única vía que contempla la
especificación para obtener un resultado tabular de un procedimiento es un
parámetro `REF_CURSOR`, lo que obligaría a reescribir
`fn_listar_prestamos_activos_por_usuario` y `fn_reporte_libros_mas_prestados`
para que abran y devuelvan un cursor en vez de usar `RETURNS TABLE` — y con
eso perderían la posibilidad de invocarse directo como
`SELECT * FROM fn_...(...)` desde `psql`, Postman u otra herramienta externa
de depuración/inspección. Por eso esas dos usan `@Query(nativeQuery = true)`
desde Spring Data en lugar de `@Procedure`/`@NamedStoredProcedureQuery`.
Ambos mecanismos (`@Procedure` y `@Query` nativo con parámetros nombrados)
cumplen igual la prohibición de SQL dinámico / concatenación de entrada de
usuario de la regla A.2.3: en ningún caso hay `EXECUTE`, `sp_executesql`, ni
construcción de la sentencia por concatenación de strings — la única
diferencia es el mecanismo JDBC que usa Hibernate para transportar la
llamada (`CallableStatement` vs. `PreparedStatement` con parámetros
nombrados `:p_...`).

## Convención de SQLSTATE para mapeo a HTTP en el backend

Los `RAISE EXCEPTION` de los procedimientos con efectos secundarios usan
códigos SQLSTATE personalizados de 5 caracteres para que el backend los
capture y traduzca a `ProblemDetails` sin tener que parsear el texto del
mensaje:

| SQLSTATE | Significado | HTTP sugerido |
|----------|--------------|----------------|
| `LB404`  | El recurso referenciado no existe (usuario, libro, préstamo, multa) | 404 Not Found |
| `LB409`  | Conflicto de estado: la operación ya se ejecutó o el recurso no está en el estado esperado (doble devolución, multa ya pagada/anulada) | 409 Conflict |
| `LB422`  | Violación de regla de negocio (usuario bloqueado, sin stock, rol insuficiente, configuración faltante) | 422 Unprocessable Entity |

## Catálogo

### 1. `sp_crear_prestamo`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL con efectos secundarios) |
| Propósito | Registrar un préstamo nuevo de forma atómica |
| Parámetros | `p_usuario_id BIGINT`, `p_libro_id BIGINT`, `p_bibliotecario_id BIGINT`, `p_dias_prestamo INTEGER` |
| Retorno | `BIGINT` — id del préstamo creado |
| Tablas afectadas | `usuarios` (lectura), `libros` (lectura+UPDATE), `estados_usuario` (lectura), `estados_prestamo` (lectura), `prestamos` (INSERT) |
| Justificación A.2 | Validación cruzada multi-tabla (estado del usuario en `estados_usuario` + stock en `libros`) más una transacción atómica de dos escrituras relacionadas (decrementar stock e insertar el préstamo) que deben tener éxito o fallar juntas — no es CRUD elemental de una sola tabla. |

### 2. `sp_registrar_devolucion`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL con efectos secundarios, múltiples OUT) |
| Propósito | Registrar la devolución de un préstamo y generar multa automática si hay atraso |
| Parámetros | `p_prestamo_id BIGINT` |
| Retorno | `o_prestamo_id BIGINT`, `o_hubo_multa BOOLEAN`, `o_monto_multa NUMERIC(8,2)` |
| Tablas afectadas | `prestamos` (lectura+UPDATE), `libros` (UPDATE), `estados_prestamo` (lectura), `configuracion_sistema` (lectura), `estados_multa` (lectura), `multas` (INSERT condicional), `estados_usuario` (lectura), `usuarios` (UPDATE condicional) |
| Justificación A.2 | Hasta 4 tablas escritas/leídas en una sola transacción condicionada por lógica de negocio (cálculo de días de atraso, lectura de configuración, generación de multa, bloqueo de usuario) — imposible de expresar como CRUD elemental sin exponer la lógica de negocio en el backend con múltiples round-trips no atómicos. |

### 3. `sp_pagar_multa`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL con efectos secundarios, múltiples OUT) |
| Propósito | Registrar el pago de una multa y reactivar al usuario si no tiene otras pendientes |
| Parámetros | `p_multa_id BIGINT` |
| Retorno | `o_multa_id BIGINT`, `o_usuario_desbloqueado BOOLEAN` |
| Tablas afectadas | `multas` (lectura+UPDATE), `prestamos` (lectura, JOIN para ubicar al usuario), `estados_multa` (lectura), `usuarios` (UPDATE condicional), `estados_usuario` (lectura) |
| Justificación A.2 | Validación cruzada multi-tabla: el desbloqueo del usuario depende de un COUNT agregado sobre TODAS sus multas (vía JOIN `multas`↔`prestamos`), no solo la multa actual — una condición que ningún método CRUD derivado de Spring Data puede expresar. |

### 4. `sp_anular_multa`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL con efectos secundarios, múltiples OUT) |
| Propósito | Anular una multa pendiente (solo GERENTE/ADMIN) y auditar la operación |
| Parámetros | `p_multa_id BIGINT`, `p_motivo VARCHAR(255)`, `p_rol_ejecutor VARCHAR(30)` |
| Retorno | `o_multa_id BIGINT`, `o_usuario_desbloqueado BOOLEAN` |
| Tablas afectadas | `multas` (lectura+UPDATE), `prestamos` (lectura), `estados_multa` (lectura), `usuarios` (UPDATE condicional), `estados_usuario` (lectura), `bitacora_auditoria` (INSERT) |
| Justificación A.2 | Misma justificación que `sp_pagar_multa` (agregación multi-tabla para el desbloqueo) más una segunda barrera de autorización a nivel de datos y una escritura de auditoría atómica con la anulación — ambas deben ocurrir en la misma transacción o ninguna. |

### 5. `fn_listar_prestamos_activos_por_usuario`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL puro, `STABLE`, retorna `TABLE`) |
| Propósito | Proyección heterogénea de los préstamos no devueltos de un usuario, con datos del libro y del estado |
| Parámetros | `p_usuario_id BIGINT` |
| Retorno | `TABLE(prestamo_id, libro_titulo, libro_isbn, fecha_prestamo, fecha_devolucion_estimada, dias_restantes, estado_nombre)` |
| Tablas afectadas | Solo lectura: `prestamos`, `libros`, `estados_prestamo` |
| Justificación A.2 | JOIN de 3 tablas con una columna calculada (`dias_restantes`) — exactamente el caso que la guía nombra como obligatorio implementar como función en vez de una entidad/proyección JPA. |

### 6. `sp_expirar_reservaciones_vencidas`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL con efectos secundarios, UPDATE masivo) |
| Propósito | Expirar en lote todas las reservaciones vencidas no retiradas |
| Parámetros | `p_ahora TIMESTAMPTZ DEFAULT NOW()` (parametrizable para pruebas) |
| Retorno | `INTEGER` — cantidad de filas actualizadas |
| Tablas afectadas | `reservaciones` (lectura+UPDATE), `estados_reservacion` (lectura) |
| Justificación A.2 | UPDATE masivo condicionado (`estado IN (...) AND fecha < ...`) pensado para ejecutarse como job periódico — no es una operación CRUD sobre un registro individual, sino una transacción atómica sobre un conjunto. |

### 7. `fn_reporte_libros_mas_prestados`

| Campo | Detalle |
|-------|---------|
| Tipo | Función (SQL puro, `STABLE`, retorna `TABLE`) |
| Propósito | Ranking de libros más prestados, con filtro opcional de rango de fechas |
| Parámetros | `p_limite INTEGER DEFAULT 10`, `p_desde TIMESTAMPTZ DEFAULT NULL`, `p_hasta TIMESTAMPTZ DEFAULT NULL` |
| Retorno | `TABLE(libro_id, titulo, isbn, total_prestamos)` |
| Tablas afectadas | Solo lectura: `prestamos`, `libros` |
| Justificación A.2 | JOIN + agregación (`GROUP BY`/`COUNT`/`ORDER BY`/`LIMIT`) — el ejemplo canónico de "agregación" que la guía reserva para SP/función, imposible de expresar como método derivado de Spring Data. |

## Validación

Los 7 objetos fueron aplicados y probados manualmente contra una instancia
real de PostgreSQL 16 (contenedor `sgb_postgres`), cubriendo: creación de
préstamo exitosa y sus 3 validaciones de error (usuario inexistente, libro
inexistente, sin stock/usuario bloqueado), devolución a tiempo y devolución
tardía con generación de multa y bloqueo de usuario, doble devolución
(rechazada), pago de multa con desbloqueo, doble pago (rechazado),
anulación con rol inválido (rechazada) y con rol válido (incluye
verificación de la fila en `bitacora_auditoria`), listado de préstamos
activos, reporte de libros más prestados, y expiración masiva de
reservaciones vencidas. Todos los casos se comportaron según lo
especificado.

Adicionalmente se validó el flujo completo de inicialización automática
descrito abajo (`scripts/build-init-sql.sh` + `make up`) contra un volumen
Postgres vacío: las 26 tablas, las 8 funciones (7 procs + el trigger de
`actualizado_en`) y todos los `INSERT` de `db/seed.sql` se aplican en una
sola pasada, sin errores, incluyendo un `sp_crear_prestamo` de humo
inmediatamente después.

## Reproducibilidad automática: `scripts/build-init-sql.sh`

`db/procs/*.sql` vive en un subdirectorio de `db/`, y PostgreSQL **no**
recorre subdirectorios de `docker-entrypoint-initdb.d/` — si se montara
`db/` completo, los 7 archivos de `procs/` nunca se ejecutarían en un
`make up` desde cero (bug real detectado y cerrado, no solo señalado).

Solución adoptada: `scripts/build-init-sql.sh` concatena
`db/schema.sql` + `db/procs/*.sql` (orden alfabético) + `db/seed.sql` (en
ese orden) en un único `db/init/01-consolidado.sql` generado (carpeta
`db/init/` gitignored, se regenera en cada `make up`; el script falla con
`exit 1` y mensaje claro si falta cualquiera de los 3 orígenes).
`docker-compose.yml` monta la carpeta `db/init/` completa en
`docker-entrypoint-initdb.d/` — no `db/` completo. Las fuentes de verdad
para revisión y edición siguen siendo `db/schema.sql`, cada archivo
individual de `db/procs/` y `db/seed.sql`; `db/init/01-consolidado.sql` es
un artefacto de build, nunca se edita a mano.

Validado contra un volumen Postgres vacío (`docker compose down -v` +
`make up`): las 26 tablas, el seed y los 7 procedimientos quedan
disponibles en una sola pasada de inicialización (evidencia en la sección
"Validación" arriba y en el resumen de la conversación).

## Pendiente (seguimiento obligatorio para el prompt de Cajas)

Los 3 procedimientos con múltiples parámetros OUT resueltos vía
`@NamedStoredProcedureQuery` (`sp_registrar_devolucion`, `sp_pagar_multa`,
`sp_anular_multa`, ver `MultaProcedureRepository`/`PrestamoProcedureRepository`)
compilan pero **no están verificados en runtime** contra Hibernate/pgjdbc —
es un área conocida como frágil en esa combinación específica. El prompt
que arme la capa de servicio (Cajas) debe incluir, como condición para dar
por cerrada la integración, un test de integración explícito
(`@SpringBootTest` contra Postgres real o Testcontainers) que invoque estos
3 procedimientos de punta a punta — no basta con que el código compile.
