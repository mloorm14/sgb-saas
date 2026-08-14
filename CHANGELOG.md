# Changelog

Todos los cambios notables de este proyecto se documentan en este archivo,
siguiendo la convención [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y [Semantic Versioning](docs/VERSIONING.md).

Cada entrada referencia el hash corto del commit real donde ocurrió el
cambio (`git show <hash>` para el detalle completo); no se listan cambios
que no correspondan a un commit existente en el historial.

## [Unreleased]

Cambios acumulados desde el tag `v0.1.0-entrega-1b` (commit `1f89354`,
cierre de Entrega 1B) hasta el estado actual de `main`, camino al tag
objetivo `v0.9.0-rc` de esta Tercera Entrega.

### Added

- Modelo de datos completo del dominio: 26 tablas en `db/schema.sql` con
  seed de administrador y libros de ejemplo (`fbee895`).
- Entidades y repositorios JPA para préstamos, reservaciones y multas
  (`8613192`).
- 7 procedimientos/funciones almacenadas de PostgreSQL para el módulo de
  préstamos, con carga automatizada vía `scripts/build-init-sql.sh`
  (`c53edde`).
- Soporte multi-rol en `UserDetailsServiceImpl`/`JwtService`, sobre el
  modelo RBAC normalizado (`roles`, `usuario_roles`, `permisos`,
  `rol_permisos`) (`7e75215`).
- Constraints `CHECK` e índices en `reservaciones`/`prestamos` (`0776bc7`).
- TTL configurable externamente (`CACHE_LIBROS_TTL_SECONDS`) para el
  cache Redis del catálogo de libros (`cabf563`).
- `scripts/mediciones-header.sh` y convención de plantilla/nombres para
  evidencia en `docs/mediciones/` (`3381c29`, `65c9969`).
- Configuración base de k6 (perfil de VUs/duración) para la prueba de
  carga del Bloque C.1 (`4846ee8`).
- `LICENSE` (MIT) (`468673e`).
- 6 nuevos ADRs (`ADR-007` cookies JWT, `ADR-008` TTL de cache,
  `ADR-009` licencia MIT, `ADR-010` autenticación JWT+RBAC, `ADR-011`
  gestor de base de datos, `ADR-012` estrategia de despliegue,
  `ADR-013` acceso a datos ORM+SP) y ampliación de `ADR-001` a la pila
  principal completa (`6b58c89`, `239105d`, `468673e`, `4140488`,
  `2ed3d2a`, `587e4c6`, `3f7f177`, `a38f3a4`), con índice de mapeo en
  `docs/adr/README.md` (`5e05924`).
- `docs/arquitectura/ISO25010.md` (tabla de atributos de calidad
  ISO/IEC 25010) y `docs/arquitectura/workspace.dsl` (modelo C4 niveles
  1-2 en Structurizr DSL) (`43caad0`, `83b4889`).
- Bitácora de observaciones del equipo en `docs/observaciones/`
  (`d162421`).
- Módulo H (asistente virtual con Gemini): migración `V9__chatbot.sql`
  con `sesiones_chat`, `mensajes_chat` y `base_conocimiento` (seed de
  preguntas frecuentes) (`a66ba9b`); entidades, repositorios y DTOs del
  chatbot (`3286a33`, `35be73f`, `f6f7a09`, `fd56c78`, `8d0718b`,
  `2536498`, `8f184e6`, `cc43935`); `ChatbotRateLimiter` y excepciones
  propias de rate limit/sesión (`d1262aa`, `600d25d`, `894a7f2`);
  `GeminiClient` (HTTP directo con `RestClient`, sin SDK ni dependencias
  nuevas) (`b03efba`); `ChatbotService` que orquesta sesiones, grounding
  e intención (`3673c4a`); `ChatbotController` con `POST
  /api/v1/chat/mensajes` y `GET /api/v1/chat/sesiones/{id}/historial`
  para LECTOR (`75b1c9c`); tests unitarios, de seguridad y de rate limit
  (`1fbc6d5`, `233702d`, `eed4642`) y prueba de integración contra
  Gemini real deshabilitada por defecto (`35c79b9`).
- Documentación de despliegue de producción en
  `docs/despliegue/DEPLOYMENT.md` (arquitectura Render + Neon + Upstash,
  límites del free tier, variables de entorno solo nombres y
  procedimiento desde cero) (`88fae53`), `RUNBOOK.md` (suspend/resume,
  rotación de secretos y contenedores, restauración) (`014e9a1`) y
  `BACKUP.md` (PITR de Neon, retención hasta el 2026-09-16 y prueba de
  restauración) (`b8b1ee8`).
- Usuario demo de evaluación `u@uteq.edu.ec` / `usuario1` con rol
  limitado LECTOR, separado del admin real, vía migración
  `V11__seed_usuario_demo.sql` (`df741b9`) con su espejo local en
  `db/seed.sql` (`2982589`).

### Changed

- `Usuario`/`Libro` migrados de campos simples a modelo RBAC normalizado
  (`roles`, `estados`) (`6609d80`).
- `LibroService`: constante literal extraída, `moduleResolution` de
  `tsconfig` corregido (`f8c4d60`).
- Se permite más de una multa por préstamo, eliminando la restricción
  `UNIQUE` previa sobre `multas.prestamo_id` (`e789c04`).
- Imágenes base de Docker Compose pinadas por digest sha256 en vez de
  tag flotante (`postgres`, `redis`, `eclipse-temurin` build/runtime,
  `node`, `nginx`) (`b747ef0`).
- Build context y ubicación de migraciones Flyway ajustados en el
  contenedor `backend` (`6f0645f`); `.env.example` completado en
  preparación del pinning de digests (`4b6dec7`).
- Tests de `AuthServiceTest`/`LibroServiceTest` actualizados al modelo
  RBAC (`81a06ec`).
- Documentación de `db/roles-privilegios.sql` corregida (propuesta de
  roles y privilegios) (`f45d481`); README actualizado con paso de
  ejecución de tests (`072175f`).
- `docs/adr/README.md`: ADR-014 y ADR-015 agregados a la tabla de
  "Otros ADRs" (existían como archivo pero no estaban indexados)
  (`65ccf8d`).
- `application.yml`: nueva sección `app.gemini` (api-key, modelo,
  url-base, timeout y rate-limit del chatbot) con defaults vía
  `${VAR:default}` para que el contexto cargue sin credenciales
  (`48d0897`); `.env.example` documenta las variables opcionales del
  Módulo H (`895514c`).
- `docs/adr/adr-012-estrategia-despliegue.md`: sección de Actualización
  fechada (2026-08-13) — la producción pasa a Render + Neon + Upstash
  (descartada la VM Oracle Cloud ARM por saturación del Always Free);
  Docker Compose queda vigente para local/evaluación (`540f7c9`).
- README: se publica la URL del sistema desplegado
  (https://biblora-sgb.onrender.com + backend y health check) reemplazando
  el placeholder de la Entrega 2 (`a9f6451`) y se agrega la sección
  "Cuenta demo (para evaluación)" con las credenciales públicas del
  usuario LECTOR, dejando intacta la advertencia del admin real
  (`37fa6f3`).

### Fixed

- 10 issues de QA: Dockerfile del frontend, `.env.example`,
  `GlobalExceptionHandler`, cache Redis, `@CacheEvict`, IDs en
  `LibroResponseDTO`, loop del interceptor JWT, README, `pom.xml`
  (`00ecff4`).
- Dependencia incorrecta `redis-reactive` corregida a `redis`;
  `@Transactional` explícito agregado en `LibroService` (`1c30b2e`).
- Fuente única de migraciones Flyway unificada en
  `database/migrations/` (antes duplicada) (`03821d7`).
- `GlobalExceptionHandler` responde RFC 7807 (`ProblemDetail`) en todos
  los handlers, incluyendo los que faltaban para `LockedException`
  (cuenta bloqueada por multas → 423) y `DisabledException` (cuenta
  inactiva → 403), que antes caían al catch-all genérico y devolvían
  500 sin distinción (`0f1b980`).
- Cache Redis del catálogo (`"libros"`) nunca servía una lectura real:
  incompatibilidad entre `GenericJackson2JsonRedisSerializer` y
  `Page`/`PageImpl` causaba una excepción no controlada en cada intento
  de lectura del cache; corregido migrando ese cache a serialización
  Java estándar (`cabf563`).
- `docs/postman/coleccion.json` no documentaba   los endpoints de
  `UsuarioAdminController` (`GET /api/v1/admin/usuarios`, `PATCH
  /{id}/rol`, `PATCH /{id}/estado`) ni de `AuditoriaController` (`GET
  /api/v1/auditoria`) del Módulo 5/6, pese a que la convención del
  proyecto exige documentar todo endpoint nuevo en la colección; se
  agregan las carpetas "Usuarios (Admin)" y "Auditoria" con casos de
  éxito (200/204) y de error (400/403) para ADMIN, GERENTE y LECTOR
  (`d5424e8`).
- `ReportePdfService` no compilaba con iText 9.5 (`Paragraph.setBold()`
  eliminado en esa versión) y reutilizaba una fuente `static` que iText
  invalida entre documentos, haciendo fallar `ReportePdfServiceTest`;
  se fija explícitamente Helvetica-Bold por documento (`a63ae00`).
- `BackendApplicationTests.contextLoads()` fallaba con "No qualifying
  bean of type 'AutorRepository'": los repositorios de la
  feature/catalogo (Autor, Categoria, Favorito, SugerenciaAdquisicion)
  no estaban mockeados; se completan los `@MockitoBean` siguiendo el
  patrón documentado en la clase (`927f4f0`).
- Login del usuario demo devolvía 403: `u@uteq.edu.ec` ya existía en la
  base en estado `PENDIENTE_VERIFICACION` (creado por el registro
  público) y el `ON CONFLICT DO NOTHING` de V11 no corregía ese estado;
  `V12__fix_usuario_demo.sql` normaliza la cuenta de forma idempotente
  (ACTIVO + correo verificado + hash de `usuario1` + rol LECTOR)
  (`8b1fa2d`).

### Security

- `refreshToken` migrado de cuerpo JSON en texto plano a cookie
  `HttpOnly`+`Secure`+`SameSite=Strict` con `path=/api/auth` (`1dfc4f8`).
- `AuthorizationDeniedException` ahora responde 403 RFC 7807 en vez de
  caer como 500 no controlado; se agrega logueo de errores 500
  genuinos no controlados (`24ea873`).
- OWASP A05 (Módulo 10.2, REQ-NF-014): `Content-Security-Policy`
  explícito en `SecurityConfig` para el backend, ausente antes
  (`42fe942`).
- OWASP A05 (Módulo 10.2, REQ-NF-014): nuevo perfil `prod` en
  `application.yml` que deshabilita Swagger UI/OpenAPI y oculta
  stacktraces/mensajes internos en el path `/error` por defecto
  (`853c0db`).
- OWASP A05 (Módulo 10.2, REQ-NF-014): la imagen Docker del backend
  ahora corre como usuario no-root (`spring`) en vez de `root`
  (`dbbc097`).
- REQ-NF-012 (Módulo 10.1): `ADR-015` documenta la decisión de que TLS
  termina en el proxy (no en el backend Spring Boot), y
  `server.forward-headers-strategy: framework` (`application.yml`) deja
  el backend listo para reconocer `X-Forwarded-Proto` y activar
  `Strict-Transport-Security` en cuanto exista TLS real delante del
  stack (`381816c`, `853c0db`).
- OWASP A05 (Módulo 10.2, REQ-NF-014): `SPRING_PROFILES_ACTIVE` expuesto
  en el servicio `backend` de `docker-compose.yml`, con default vacío
  para no deshabilitar Swagger en desarrollo local; se activa el perfil
  `prod` solo definiéndolo en `.env` (`f404a4d`, documentado en
  `.env.example` en `d91d552`).
- ADR-016 documenta la decisión de privacidad del chatbot con Gemini:
  el prompt solo incluye el texto del mensaje, el historial de la
  sesión y el grounding (`base_conocimiento` + sugerencias de
  disponibilidad); nunca datos personales ni credenciales; las reservas
  desde el chat se difieren a v2 (`f4cfc70`), indexado en
  `docs/adr/README.md` (`e41cf51`). Trazabilidad: REQ-F-028 agregado a
  la matriz (`d2be0ad`) y carpeta "Chatbot" en la colección de Postman
  (`b6da8bc`).

## [v0.1.0-entrega-1b] — 2026-06-20

Cierre de la Entrega 1B (commit `1f89354`). Changelog detallado de esta
versión y anteriores no reconstruido retroactivamente — fuera del
alcance de esta tarea (Bloque E de la Tercera Entrega); el historial
completo permanece disponible vía `git log`.

[Unreleased]: https://github.com/mloorm14/sgb-saas/compare/v0.1.0-entrega-1b...HEAD
[v0.1.0-entrega-1b]: https://github.com/mloorm14/sgb-saas/releases/tag/v0.1.0-entrega-1b