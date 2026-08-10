# Índice de ADRs — SGB-SaaS

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
