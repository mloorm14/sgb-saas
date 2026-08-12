# Índice de ADRs — SGB-SaaS

## Mapeo con la guía de la Entrega Final

La guía de la Entrega Final (Bloque A.2.2 y B.8) exige un ADR-006 sobre
"Estrategia de acceso a datos: separación CRUD/SP" y un ADR-007 sobre
estrategia de despliegue. En este repositorio esos contenidos existen con
otra numeración, por continuidad con la numeración ya usada y evaluada en
la Entrega 3 (renumerar ahora rompería ~30 referencias cruzadas en
SRS.md, el informe LaTeX, código Java y CHANGELOG.md):

- Estrategia de acceso a datos (CRUD-ORM / stored procedures): ver
  [ADR-013](adr-013-acceso-datos-orm-sp.md).
- Estrategia de despliegue: ver [ADR-012](adr-012-estrategia-despliegue.md).

Ambos ADRs cubren el contenido exigido por la guía; solo difiere el
número de secuencia respecto a la numeración sugerida en la guía.

Mapeo de los 6 temas obligatorios del Bloque D contra los ADRs que los
cubren. Generado tras la auditoría de ADRs de la Tercera Entrega, que
detectó 3 temas parcialmente cubiertos y 3 sin ADR — todos cerrados con
los ADR-010 a ADR-013 y la ampliación de ADR-001 y ADR-008.

| # | Tema obligatorio (Bloque D) | ADR(s) | Cobertura |
|---|---|---|---|
| 1 | Elección de la pila principal | [ADR-001-tecnologia.md](ADR-001-tecnologia.md) | Completa — backend (Spring Boot), frontend (Angular), base de datos (PostgreSQL) y cache (Redis) y orquestación (Docker Compose) con referencia cruzada a su ADR dedicado |
| 2 | Esquema de autenticación | [adr-010-autenticacion-jwt-rbac.md](adr-010-autenticacion-jwt-rbac.md) + [ADR-003-jwt-redis.md](ADR-003-jwt-redis.md) + [adr-007-cookies-jwt.md](adr-007-cookies-jwt.md) | Completa — decisión raíz (JWT stateless + RBAC normalizado) en ADR-010, revocación en ADR-003, transporte en adr-007 |
| 3 | Gestor de base de datos | [adr-011-gestor-base-datos.md](adr-011-gestor-base-datos.md) | Completa — PostgreSQL sobre MySQL/MongoDB, con foco en RLS y soporte de PL/pgSQL |
| 4 | Estrategia de cache | [adr-008-ttl-cache-libros.md](adr-008-ttl-cache-libros.md) | Completa — sección "Contexto ampliado" cubre por qué Redis como cache de aplicación; el resto del archivo cubre el TTL específico |
| 5 | Estrategia de despliegue | [adr-012-estrategia-despliegue.md](adr-012-estrategia-despliegue.md) | Completa — Docker Compose sobre Kubernetes/despliegue manual |
| 6 | Estrategia de acceso a datos (CRUD-ORM vs SPs) | [adr-013-acceso-datos-orm-sp.md](adr-013-acceso-datos-orm-sp.md) | Completa — decisión híbrida formalizada como ADR; detalle técnico de cada procedimiento en `docs/basedatos/CATALOGO-SP.md` |

## Otros ADRs (no mapeados a los 6 temas obligatorios)

| ADR | Tema |
|---|---|
| [adr-006-estrategia-schema-reproducible.md](adr-006-estrategia-schema-reproducible.md) | Versionado de esquema (Flyway + snapshot reproducible) |
| [adr-009-licencia-mit.md](adr-009-licencia-mit.md) | Licencia del proyecto (MIT) |
| [adr-014-separacion-admin-gerente.md](adr-014-separacion-admin-gerente.md) | Separación de responsabilidades entre ADMIN y GERENTE (Módulo 5) |
| [adr-015-tls-transporte.md](adr-015-tls-transporte.md) | Estrategia de TLS en tránsito: termina en el proxy, no en el backend (Módulo 10.1) |
| [adr-016-gemini-privacidad.md](adr-016-gemini-privacidad.md) | Datos enviados a Gemini en el chatbot (Módulo H) y por qué no es exposición indebida |
