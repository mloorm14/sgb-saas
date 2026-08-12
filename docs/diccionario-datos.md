# Diccionario de datos — SGB-SaaS

> **Procedencia**: este documento se generó consultando directamente el
> esquema real de PostgreSQL (`information_schema` + `pg_catalog`) sobre una
> base de datos levantada desde cero (`docker compose down -v && docker
> compose up -d --build`), es decir, tal como Flyway la deja tras aplicar
> las 9 migraciones (`V1`…`V9`, baseline en `V3` desde `db/schema.sql`) —
> **no** es una transcripción manual de los archivos `.sql` fuente. Refleja
> columnas, tipos, nullabilidad, defaults, claves e índices exactamente como
> quedan aplicados.
>
> - Generado: **2026-08-11**
> - Commit de `main`: `3b1887387c0cc4ad14ddb68b8197561b8479ef6f`
> - PostgreSQL 16.14, extensiones `uuid-ossp`, `pgcrypto` y `pg_trgm`
> - 31 tablas de datos (excluye `flyway_schema_history`, que es
>   metadata interna de Flyway, no un dato de negocio)

## Notas transversales del esquema

- **Dos generadores de UUID distintos conviven en el esquema**:
  `usuarios.credencial_qr_token` usa `uuid_generate_v4()` (extensión
  `uuid-ossp`, Módulo credencial QR), mientras que `sesiones_chat.id` usa
  `gen_random_uuid()` (extensión `pgcrypto`, Módulo chatbot). Son
  criptográficamente equivalentes (ambos generan UUID v4), pero al venir de
  extensiones distintas, cualquier migración futura que dependa de una
  extensión específica debe tenerlo presente.
- **Varias restricciones `UNIQUE` de una sola columna están implementadas
  como `CREATE UNIQUE INDEX` en vez de `ADD CONSTRAINT ... UNIQUE`** (ej.
  `usuarios.correo`, `usuarios.credencial_qr_token`, `libros.isbn`).
  Funcionalmente son equivalentes, pero no aparecen en
  `information_schema.table_constraints` — hay que consultar
  `pg_indexes`/`pg_catalog` para verlas, tal como hizo el script de este
  diccionario.
- **Ningún objeto del esquema tiene `COMMENT ON` en la base real** — los
  comentarios explicativos existen únicamente en los archivos `.sql` fuente
  (`database/migrations/`), no en el catálogo de PostgreSQL.
- **`multas.prestamo_id` no tiene `UNIQUE`** (fue removido explícitamente en
  `V3__multas_multiples_por_prestamo.sql`): un préstamo puede acumular más
  de una multa.
- **Módulo "Credencial QR"**: no existe una tabla dedicada — el token vive
  como la columna `usuarios.credencial_qr_token` (`UUID`, `DEFAULT
  uuid_generate_v4()`, `UNIQUE` vía índice), generado por Postgres al
  insertar el usuario. Ver la sección [Usuarios / RBAC /
  Autenticación](#usuarios--rbac--autenticación).
- **Módulo "Búsqueda"**: tampoco tiene tabla propia — la búsqueda
  predictiva (Módulo 8, migración `V8__busqueda_predictiva.sql`) se
  implementa como un índice `GIN` con `gin_trgm_ops` (extensión `pg_trgm`)
  sobre `libros.titulo` (`idx_libros_titulo_trgm`), para similitud de texto
  en el autocompletado del catálogo. Ver la sección
  [Catálogo](#catálogo).


## Usuarios / RBAC / Autenticación

### `usuarios`

Cuenta de cada persona del sistema (lector, bibliotecario, administrador). Incluye credenciales, estado de la cuenta y el token de credencial QR.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('usuarios_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(100) | No |  |  |
| `apellido` | VARCHAR(100) | No |  |  |
| `correo` | VARCHAR(150) | No |  | UNIQUE (índice) |
| `password_hash` | VARCHAR(255) | No |  |  |
| `identificacion_usuario` | VARCHAR(20) | Sí |  |  |
| `estado_id` | INTEGER | No |  | FK -> `estados_usuario.id` |
| `correo_verificado` | BOOLEAN | No | `false` |  |
| `fecha_registro` | TIMESTAMPTZ | No | `now()` |  |
| `actualizado_en` | TIMESTAMPTZ | No | `now()` |  |
| `credencial_qr_token` | UUID | No | `uuid_generate_v4()` | UNIQUE (índice) |

### `roles`

Catálogo de roles del sistema (RBAC) — ej. LECTOR, BIBLIOTECARIO, ADMIN.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('roles_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |
| `descripcion` | VARCHAR(200) | Sí |  |  |

**Índices adicionales:**

- `roles_nombre_key` (único): `CREATE UNIQUE INDEX roles_nombre_key ON public.roles USING btree (nombre)`

### `permisos`

Catálogo de permisos individuales que pueden asignarse a un rol.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('permisos_id_seq'::regclass)` | PK |
| `codigo` | VARCHAR(60) | No |  | UNIQUE |

**Índices adicionales:**

- `permisos_codigo_key` (único): `CREATE UNIQUE INDEX permisos_codigo_key ON public.permisos USING btree (codigo)`

### `rol_permisos`

Tabla de unión N:M entre `roles` y `permisos`.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `rol_id` | INTEGER | No |  | PK (compuesta); FK -> `roles.id` |
| `permiso_id` | INTEGER | No |  | PK (compuesta); FK -> `permisos.id` |

### `usuario_roles`

Tabla de unión N:M entre `usuarios` y `roles` — un usuario puede tener más de un rol.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `usuario_id` | BIGINT | No |  | PK (compuesta); FK -> `usuarios.id` |
| `rol_id` | INTEGER | No |  | PK (compuesta); FK -> `roles.id` |
| `asignado_en` | TIMESTAMPTZ | No | `now()` |  |

### `estados_usuario`

Catálogo de estados posibles de una cuenta de usuario (ej. `PENDIENTE_VERIFICACION`, `ACTIVO`, `BLOQUEADO`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('estados_usuario_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `estados_usuario_nombre_key` (único): `CREATE UNIQUE INDEX estados_usuario_nombre_key ON public.estados_usuario USING btree (nombre)`

### `tokens_invalidos`

Lista negra de JWT (`jti`) invalidados antes de su expiración natural — usada en logout y en el flujo de refresh token.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('tokens_invalidos_id_seq'::regclass)` | PK |
| `jti` | VARCHAR(100) | No |  | UNIQUE |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `expira_en` | TIMESTAMPTZ | No |  |  |
| `invalidado_en` | TIMESTAMPTZ | No | `now()` |  |

**Índices adicionales:**

- `tokens_invalidos_jti_key` (único): `CREATE UNIQUE INDEX tokens_invalidos_jti_key ON public.tokens_invalidos USING btree (jti)`

### `verificaciones_correo`

Tokens de un solo uso para el flujo de verificación de correo tras el registro (Módulo 2/9.5).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('verificaciones_correo_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `token` | UUID | No | `uuid_generate_v4()` |  |
| `expira_en` | TIMESTAMPTZ | No |  |  |
| `usado` | BOOLEAN | No | `false` |  |
| `creado_en` | TIMESTAMPTZ | No | `now()` |  |

## Catálogo

### `libros`

Catálogo de libros físicos disponibles para préstamo, con stock total/disponible y metadatos bibliográficos.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('libros_id_seq'::regclass)` | PK |
| `isbn` | VARCHAR(13) | No |  | UNIQUE (índice) |
| `titulo` | VARCHAR(255) | No |  |  |
| `resumen` | TEXT | Sí |  |  |
| `portada_url` | VARCHAR(1000) | Sí |  |  |
| `anio_publicacion` | SMALLINT | No |  | CHECK: `CHECK (((anio_publicacion >= 1000) AND (anio_publicacion <= 2100)))` |
| `editorial_id` | INTEGER | No |  | FK -> `editoriales.id` |
| `idioma_id` | INTEGER | No |  | FK -> `idiomas.id` |
| `estado_id` | INTEGER | No |  | FK -> `estados_libro.id` |
| `stock_total` | SMALLINT | No | `1` | CHECK: `CHECK ((stock_total >= 0))`; CHECK: `CHECK ((stock_disponible <= stock_total))` |
| `stock_disponible` | SMALLINT | No | `1` | CHECK: `CHECK ((stock_disponible >= 0))`; CHECK: `CHECK ((stock_disponible <= stock_total))` |
| `ubicacion_fisica` | VARCHAR(50) | Sí |  |  |
| `fecha_registro` | TIMESTAMPTZ | No | `now()` |  |
| `actualizado_en` | TIMESTAMPTZ | No | `now()` |  |

**Índices adicionales:**

- `idx_libros_titulo_trgm` (no único): `CREATE INDEX idx_libros_titulo_trgm ON public.libros USING gin (titulo gin_trgm_ops)`

### `autores`

Catálogo de autores.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('autores_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(150) | No |  |  |

### `categorias`

Catálogo de categorías/géneros de libros.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('categorias_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(80) | No |  | UNIQUE |

**Índices adicionales:**

- `categorias_nombre_key` (único): `CREATE UNIQUE INDEX categorias_nombre_key ON public.categorias USING btree (nombre)`

### `editoriales`

Catálogo de editoriales.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('editoriales_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(150) | No |  | UNIQUE |
| `pais_origen` | VARCHAR(80) | Sí |  |  |

**Índices adicionales:**

- `editoriales_nombre_key` (único): `CREATE UNIQUE INDEX editoriales_nombre_key ON public.editoriales USING btree (nombre)`

### `idiomas`

Catálogo de idiomas de publicación.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('idiomas_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(50) | No |  | UNIQUE |
| `codigo_iso` | VARCHAR(5) | No |  | UNIQUE |

**Índices adicionales:**

- `idiomas_nombre_key` (único): `CREATE UNIQUE INDEX idiomas_nombre_key ON public.idiomas USING btree (nombre)`
- `idiomas_codigo_iso_key` (único): `CREATE UNIQUE INDEX idiomas_codigo_iso_key ON public.idiomas USING btree (codigo_iso)`

### `estados_libro`

Catálogo de estados de un ejemplar/título (ej. `DISPONIBLE`, `DE_BAJA`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('estados_libro_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `estados_libro_nombre_key` (único): `CREATE UNIQUE INDEX estados_libro_nombre_key ON public.estados_libro USING btree (nombre)`

### `libro_autores`

Tabla de unión N:M entre `libros` y `autores`.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `libro_id` | BIGINT | No |  | PK (compuesta); FK -> `libros.id` |
| `autor_id` | BIGINT | No |  | PK (compuesta); FK -> `autores.id` |

### `libro_categorias`

Tabla de unión N:M entre `libros` y `categorias`.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `libro_id` | BIGINT | No |  | PK (compuesta); FK -> `libros.id` |
| `categoria_id` | INTEGER | No |  | PK (compuesta); FK -> `categorias.id` |

### `favoritos`

Marcado de un libro como favorito por un usuario (Módulo 3, catálogo avanzado).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `usuario_id` | BIGINT | No |  | PK (compuesta); FK -> `usuarios.id` |
| `libro_id` | BIGINT | No |  | PK (compuesta); FK -> `libros.id` |
| `agregado_en` | TIMESTAMPTZ | No | `now()` |  |

### `sugerencias_adquisicion`

Sugerencias de compra de un título enviadas por un usuario, con flujo de revisión (`PENDIENTE`/`APROBADA`/`RECHAZADA`) por un bibliotecario/admin.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('sugerencias_adquisicion_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `titulo` | VARCHAR(255) | No |  |  |
| `autor` | VARCHAR(150) | Sí |  |  |
| `isbn` | VARCHAR(13) | Sí |  |  |
| `justificacion` | TEXT | Sí |  |  |
| `estado` | VARCHAR(20) | No | `'PENDIENTE'::character varying` | CHECK: `CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'APROBADA'::character varying, 'RECHAZADA'::character varying])::text[])))` |
| `revisado_por` | BIGINT | Sí |  | FK -> `usuarios.id` |
| `creado_en` | TIMESTAMPTZ | No | `now()` |  |

## Préstamos y Multas

### `prestamos`

Registro de cada préstamo de un libro a un usuario, incluyendo renovaciones y devolución.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('prestamos_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `libro_id` | BIGINT | No |  | FK -> `libros.id` |
| `bibliotecario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `reservacion_id` | BIGINT | Sí |  | FK -> `reservaciones.id` |
| `fecha_prestamo` | TIMESTAMPTZ | No | `now()` |  |
| `fecha_devolucion_estimada` | TIMESTAMPTZ | No |  |  |
| `fecha_devolucion_real` | TIMESTAMPTZ | Sí |  |  |
| `renovaciones_realizadas` | SMALLINT | No | `0` | CHECK: `CHECK ((renovaciones_realizadas >= 0))` |
| `estado_prestamo_id` | INTEGER | No |  | FK -> `estados_prestamo.id` |

### `reservaciones`

Reserva de un libro para retiro posterior, con fecha límite antes de expirar.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('reservaciones_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `libro_id` | BIGINT | No |  | FK -> `libros.id` |
| `estado_reservacion_id` | INTEGER | No |  | FK -> `estados_reservacion.id` |
| `fecha_reserva` | TIMESTAMPTZ | No | `now()` |  |
| `fecha_limite_retiro` | TIMESTAMPTZ | No |  |  |

### `multas`

Multa asociada a un préstamo (ej. por atraso), puede haber varias por préstamo (V3 quitó el `UNIQUE` original sobre `prestamo_id`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('multas_id_seq'::regclass)` | PK |
| `prestamo_id` | BIGINT | No |  | FK -> `prestamos.id` |
| `monto` | NUMERIC(8,2) | No |  | CHECK: `CHECK ((monto > (0)::numeric))` |
| `estado_multa_id` | INTEGER | No |  | FK -> `estados_multa.id` |
| `fecha_generada` | TIMESTAMPTZ | No | `now()` |  |
| `fecha_pagada` | TIMESTAMPTZ | Sí |  |  |
| `observaciones` | VARCHAR(255) | Sí |  |  |

### `estados_prestamo`

Catálogo de estados de un préstamo (ej. `ACTIVO`, `DEVUELTO`, `VENCIDO`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('estados_prestamo_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `estados_prestamo_nombre_key` (único): `CREATE UNIQUE INDEX estados_prestamo_nombre_key ON public.estados_prestamo USING btree (nombre)`

### `estados_reservacion`

Catálogo de estados de una reservación (ej. `PENDIENTE`, `EXPIRADA`, `RETIRADA`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('estados_reservacion_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `estados_reservacion_nombre_key` (único): `CREATE UNIQUE INDEX estados_reservacion_nombre_key ON public.estados_reservacion USING btree (nombre)`

### `estados_multa`

Catálogo de estados de una multa (ej. `PENDIENTE`, `PAGADA`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('estados_multa_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `estados_multa_nombre_key` (único): `CREATE UNIQUE INDEX estados_multa_nombre_key ON public.estados_multa USING btree (nombre)`

## Notificaciones

### `notificaciones`

Notificación enviada (o intentada) a un usuario, ej. aviso de vencimiento próximo de un préstamo.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('notificaciones_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `prestamo_id` | BIGINT | Sí |  | FK -> `prestamos.id` |
| `tipo_notificacion_id` | INTEGER | No |  | FK -> `tipos_notificacion.id` |
| `mensaje` | TEXT | No |  |  |
| `fecha_envio` | TIMESTAMPTZ | Sí |  |  |
| `enviado_ok` | BOOLEAN | No | `false` |  |
| `error_envio` | VARCHAR(255) | Sí |  |  |
| `creado_en` | TIMESTAMPTZ | No | `now()` |  |

**Índices adicionales:**

- `idx_notificaciones_usuario` (no único): `CREATE INDEX idx_notificaciones_usuario ON public.notificaciones USING btree (usuario_id)`

### `tipos_notificacion`

Catálogo de tipos de notificación (ej. `VENCIMIENTO_PROXIMO`).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('tipos_notificacion_id_seq'::regclass)` | PK |
| `nombre` | VARCHAR(30) | No |  | UNIQUE |

**Índices adicionales:**

- `tipos_notificacion_nombre_key` (único): `CREATE UNIQUE INDEX tipos_notificacion_nombre_key ON public.tipos_notificacion USING btree (nombre)`

## Chatbot (asistente virtual)

### `sesiones_chat`

Sesión de conversación de un usuario con el asistente virtual (chatbot, Módulo H).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | UUID | No | `gen_random_uuid()` | PK |
| `usuario_id` | BIGINT | No |  | FK -> `usuarios.id` |
| `creado_en` | TIMESTAMPTZ | No | `now()` |  |
| `ultima_actividad` | TIMESTAMPTZ | No | `now()` |  |

### `mensajes_chat`

Cada mensaje individual (de usuario o del asistente) dentro de una `sesion_chat`.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('mensajes_chat_id_seq'::regclass)` | PK |
| `sesion_id` | UUID | No |  | FK -> `sesiones_chat.id` |
| `rol` | VARCHAR(10) | No |  | CHECK: `CHECK (((rol)::text = ANY ((ARRAY['USUARIO'::character varying, 'ASISTENTE'::character varying])::text[])))` |
| `contenido` | TEXT | No |  |  |
| `creado_en` | TIMESTAMPTZ | No | `now()` |  |

**Índices adicionales:**

- `idx_mensajes_chat_sesion` (no único): `CREATE INDEX idx_mensajes_chat_sesion ON public.mensajes_chat USING btree (sesion_id)`

### `base_conocimiento`

Preguntas/respuestas de referencia usadas para dar contexto (grounding) al modelo Gemini y evitar que invente disponibilidad de libros.

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | INTEGER | No | `nextval('base_conocimiento_id_seq'::regclass)` | PK |
| `categoria` | VARCHAR(40) | No |  |  |
| `pregunta_ejemplo` | TEXT | No |  |  |
| `respuesta` | TEXT | No |  |  |
| `activo` | BOOLEAN | No | `true` |  |

## Configuración

### `configuracion_sistema`

Tabla clave-valor de configuración editable en runtime por un administrador (ej. días de préstamo, máximo de renovaciones).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `clave` | VARCHAR(50) | No |  | PK |
| `valor` | VARCHAR(200) | No |  |  |

## Auditoría

### `bitacora_auditoria`

Registro de auditoría (OWASP A09) de operaciones sensibles: altas/bajas/modificaciones y eventos de autenticación (login OK/fallido, logout, verificación de correo).

| Columna | Tipo | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | BIGINT | No | `nextval('bitacora_auditoria_id_seq'::regclass)` | PK |
| `usuario_id` | BIGINT | Sí |  | FK -> `usuarios.id` |
| `tipo_operacion` | VARCHAR(20) | No |  | CHECK: `CHECK (((tipo_operacion)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying, 'LOGIN_OK'::character varying, 'LOGIN_FAIL'::character varying, 'LOGOUT'::character varying, 'CORREO_VERIFICADO'::character varying])::text[])))` |
| `tabla_afectada` | VARCHAR(50) | No |  |  |
| `registro_id` | BIGINT | Sí |  |  |
| `detalles` | TEXT | No |  |  |
| `ip_origen` | VARCHAR(45) | Sí |  |  |
| `fecha_hora` | TIMESTAMPTZ | No | `now()` |  |
