# ADR-010: JWT stateless sobre sesiones server-side, y RBAC normalizado sobre un campo de rol simple

## Title

Decisión raíz del esquema de autenticación/autorización: tokens JWT
stateless en vez de sesiones server-side, y un modelo RBAC normalizado
(`roles` + `usuario_roles` + `permisos`) en vez de un campo de texto con
el rol del usuario.

## Context

Los ADRs existentes (`ADR-003-jwt-redis.md`, `adr-012-cookies-jwt.md`)
documentan decisiones *derivadas* de que el sistema ya usa JWT: cómo se
revoca un token (blacklist en Redis) y cómo se transporta (cookie
HttpOnly para el refresh token). Ninguno de los dos documenta la decisión
raíz: **por qué JWT stateless en primer lugar**, ni la decisión, también
raíz, de **por qué el rol de un usuario vive en un modelo relacional
normalizado** (`roles`, `usuario_roles`, `permisos`, `rol_permisos` en
`db/schema.sql`) en vez de una columna simple `usuarios.rol VARCHAR`.

Este ADR cierra ese hueco: es la decisión de la que ADR-003 y adr-012 son
consecuencia, no un reemplazo de ninguno de los dos.

### Por qué JWT stateless sobre sesiones server-side

**Opciones consideradas:**

- **Sesiones server-side (`HttpSession` + almacén compartido tipo
  Spring Session/Redis):** descartado como mecanismo primario. Requiere
  que cada instancia del backend consulte (o comparta) un almacén de
  sesión en cada request, lo cual reintroduce estado de servidor que
  JWT stateless evita por diseño; además acopla la escalabilidad
  horizontal del backend a la disponibilidad de ese almacén compartido
  desde el primer request, no solo para revocación.
- **JWT completamente stateless, sin ningún mecanismo de revocación:**
  descartado por seguridad — un token robado o una sesión cerrada por el
  usuario seguiría siendo válido hasta su expiración natural, sin forma
  de invalidarlo antes. Este es exactamente el problema que
  `ADR-003-jwt-redis.md` resuelve con la blacklist.
- **JWT stateless + blacklist de revocación en Redis (elegido):**
  combina lo mejor de ambos — el backend no necesita consultar estado de
  sesión para validar la firma/expiración de un token (la mayoría de la
  verificación es local, criptográfica), y solo la revocación explícita
  (logout, compromiso de token) requiere una consulta a un almacén
  compartido. El "estado" que se comparte es mínimo (una lista de IDs
  revocados con TTL), no la sesión completa de cada usuario activo.

**Motivos concretos para este proyecto:**

- **Escalabilidad horizontal sin sticky sessions:** si el backend
  llegara a correr en más de una réplica, ninguna réplica necesita
  afinidad con el usuario que la contactó primero — cualquier réplica
  puede validar un JWT de forma independiente. Una sesión server-side en
  memoria de proceso exigiría sticky sessions o un almacén compartido
  para *toda* la sesión, no solo para la revocación.
- **Desacople frontend/backend:** el frontend Angular no depende de una
  cookie de sesión opaca gestionada enteramente por el servidor; recibe
  un token que puede inspeccionar (payload, no la firma) para decisiones
  de UI (ej. expiración próxima), sin necesidad de un endpoint adicional
  solo para eso.
- **El problema clásico de JWT ("no se puede invalidar antes de su
  expiración") está mitigado, no ignorado:** la combinación con la
  blacklist de `ADR-003-jwt-redis.md` da revocación inmediata sin perder
  las ventajas de statelessness para el 99% de las validaciones
  (aquellas donde el token no fue revocado).

### Por qué RBAC normalizado (`roles` + `usuario_roles` + `permisos`) sobre un campo simple

**Opciones consideradas:**

- **`usuarios.rol VARCHAR` (un rol por usuario, campo de texto):**
  descartado. No soporta un usuario con más de un rol simultáneo — un
  caso real del dominio: un bibliotecario que cubre turnos de gerente
  temporalmente necesitaría, con este diseño, una segunda cuenta
  duplicada (mismo `usuario_id` lógico, distinto registro) solo para
  tener el segundo rol, lo que rompe la integridad de auditoría
  (`bitacora_auditoria` referenciaría dos "usuarios" distintos para la
  misma persona).
- **`usuarios.rol` como enum de PostgreSQL con múltiples valores
  (array o bitmask):** descartado — resuelve el multi-rol de forma
  frágil (agregar un rol nuevo exige una migración de tipo enum, no un
  simple `INSERT`) y no permite asociar permisos granulares a un rol de
  forma consultable con SQL estándar (`rol_permisos`).
- **Modelo normalizado `roles` + `usuario_roles` (N:M) + `permisos` +
  `rol_permisos` (N:M) (elegido):** un usuario puede tener N roles
  simultáneos (`usuario_roles`), cada rol agrupa N permisos
  (`rol_permisos`), y agregar un rol o permiso nuevo es un `INSERT`, no
  una migración de esquema. Es el patrón RBAC estándar y es lo que hace
  posible, por ejemplo, que `SecurityConfig`
  (`hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')`) exprese
  autorización por rol de forma declarativa sin lógica ad-hoc.

## Decision

Se mantiene y formaliza como decisión arquitectónica (no solo como hecho
implementado) el uso de **JWT stateless con revocación vía blacklist en
Redis** como esquema de autenticación, y de un **modelo RBAC normalizado**
(`roles`, `usuario_roles`, `permisos`, `rol_permisos`) como esquema de
autorización. Ninguno de los dos es una decisión nueva — ambos ya estaban
implementados — pero no existía un ADR que documentara el *porqué* raíz
en vez de solo las decisiones derivadas.

## Status

Aceptado e implementado (documentación retroactiva de una decisión ya
tomada e implementada; no introduce cambio de código).

## Consequences

**Positivas:**

- Cierra el hueco de trazabilidad detectado en la auditoría de ADRs del
  Bloque D: "esquema de autenticación" ahora tiene un ADR que explica la
  decisión raíz, no solo sus consecuencias.
- [[ADR-003-jwt-redis]] (blacklist/revocación) y [[adr-012-cookies-jwt]]
  (transporte del refresh token en cookie HttpOnly) quedan explícitamente
  enlazados como decisiones *derivadas* de esta — un lector nuevo entiende
  el orden: primero "por qué JWT y por qué RBAC normalizado" (aquí),
  después "cómo se revoca" (ADR-003) y "cómo se transporta" (adr-012).
- El modelo RBAC normalizado ya soporta el caso de multi-rol sin cambios
  futuros de esquema (la tabla `usuario_roles` ya es N:M).

**Negativas:**

- El modelo normalizado tiene más tablas que un campo simple, y por tanto
  más JOINs al resolver los roles/permisos de un usuario en cada
  autenticación — coste aceptado y ya mitigado por el hecho de que la
  identidad se resuelve una vez por login/refresh, no en cada request
  protegida (el JWT ya lleva los roles como claim, `JwtAuthFilter` no
  vuelve a consultar `usuario_roles` en cada request).
- Como con todo JWT stateless, la ventana entre "token emitido" y
  "revocación aplicada" depende de la disponibilidad de Redis — este
  riesgo ya está documentado como pendiente de resolver en
  `ADR-003-jwt-redis.md` (fail-open vs fail-closed) y no se duplica aquí.

## Referencias

- [[ADR-003-jwt-redis]] (revocación de tokens, decisión derivada de esta)
- [[adr-012-cookies-jwt]] (transporte del refresh token, decisión derivada de esta)
- `db/schema.sql` (tablas `roles`, `usuario_roles`, `permisos`, `rol_permisos`)
- `backend-springboot/src/main/java/com/uteq/backend/config/SecurityConfig.java`
- OWASP Top 10:2021, A01 (Broken Access Control) y A07 (Identification and Authentication Failures)
