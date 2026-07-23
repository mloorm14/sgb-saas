# Reparto de trabajo — Tercera Entrega (temporal)

> **Esta carpeta se borra en un push futuro**, una vez que Cajas y Panama
> hayan completado y mergeado su parte a `main`. No es documentación
> permanente del proyecto — es un paquete de instrucciones autocontenido
> para repartir el trabajo restante de la Tercera Entrega sin que cada
> integrante tenga que pedirle contexto a Marlon.

## Qué contiene

- **[`cajas-backend/INSTRUCCIONES.md`](cajas-backend/INSTRUCCIONES.md)**
  — para Irvin Cajas: módulo de Préstamos/Devoluciones/Reservaciones/
  Multas en el backend (servicios, controllers, jobs, tests) sobre lo
  que ya existe en el repositorio.
- **[`panama-frontend/INSTRUCCIONES.md`](panama-frontend/INSTRUCCIONES.md)**
  — para Moises Panama: componentes Angular que consumen esos endpoints,
  fix del healthcheck del contenedor `sgb_frontend`, y coordinación de la
  migración pendiente del `accessToken`.

Cada archivo es autocontenido: referencia rutas de archivo exactas,
patrones de código ya existentes a seguir, y comandos concretos. No
debería hacer falta preguntarle a Marlon "¿dónde está X?" para arrancar.

## Punto de partida — estado real del proyecto a la fecha

Verificado contra el código real antes de escribir las instrucciones
(no son supuestos):

- **Backend**: Spring Boot 4.0.6 / Java 21, RBAC normalizado
  (`roles`, `usuario_roles`, `permisos` — ver `docs/adr/adr-010-autenticacion-jwt-rbac.md`),
  autenticación JWT con `accessToken` en body/header y `refreshToken` en
  cookie `HttpOnly+Secure+SameSite=Strict` (`docs/adr/adr-007-cookies-jwt.md`),
  blacklist de revocación en Redis (`ADR-003-jwt-redis.md`), cache del
  catálogo de libros con TTL externo (`adr-008-ttl-cache-libros.md`).
  Único módulo de negocio completo hoy: **Libros** (CRUD vía
  `LibroController`/`LibroService`/`LibroRepository`).
- **Base de datos**: PostgreSQL 16, 26 tablas (`db/schema.sql`), **7
  procedimientos/funciones ya escritos y probados manualmente** en
  `db/procs/` para el dominio de préstamos (catálogo completo en
  `docs/basedatos/CATALOGO-SP.md`). Las entidades JPA
  (`Prestamo`, `Reservacion`, `Multa`, `Rol`) y los repositorios (CRUD +
  "solo procedimientos") **ya existen y compilan** — no hay Service ni
  Controller todavía para ese dominio, ahí empieza el trabajo de Cajas.
- **Frontend**: Angular 17 standalone components, un módulo completo
  (`LibrosComponent`, CRUD + paginación), autenticación en memoria
  (`AuthService`/`jwt.interceptor.ts`/`auth.guard.ts`).
- **Docker**: `docker-compose.yml` con 4 servicios (`postgres`, `redis`,
  `backend`, `frontend`), imágenes pinadas por digest sha256
  (`docs/DIGESTS-LOG.md`).

## Reglas comunes para ambas partes

- Rama propia por integrante (`feature/prestamos-backend`,
  `feature/prestamos-frontend`), Conventional Commits, **PR hacia
  `main` cuando esté listo — nunca push directo a `main`** (solo Marlon
  tiene bypass configurado).
- Seguir el patrón arquitectónico ya establecido en el módulo de Libros
  (capas separadas, DTOs como `record`, RFC 7807 para errores) — no
  introducir un patrón nuevo si el existente ya resuelve el caso.
- Su parte de A.3 (historias de usuario Connextra + Gherkin, casos de
  uso Cockburn) va en `docs/requisitos/` — carpeta que **no existe
  todavía**, cada instrucción incluye una plantilla completa ya
  redactada para que la repliquen en el resto de sus historias/casos.
