# ADR-001: Elección de la pila tecnológica principal

> Nota de alcance (retomada tras la auditoría de ADRs del Bloque D): este
> ADR nació documentando solo el framework backend. Se amplía aquí para
> cubrir el resto de la pila (frontend, base de datos, cache,
> orquestación) tal como exige el Bloque D ("elección de la pila
> principal" como tema único). Donde ya existe un ADR dedicado más
> profundo para una pieza de la pila, esta sección da la justificación de
> alto nivel y remite a ese ADR para el detalle — no lo duplica.

## Backend: Spring Boot 4 (Java 21)

* **Estado:** Aceptado
* **Contexto:** El equipo necesita un lenguaje y framework del lado del servidor seguro, escalable, con soporte para JWT, ORM integrado y documentación automática de la API. Se consideró el conocimiento previo del equipo, la disponibilidad de documentación y la madurez del ecosistema.
* **Opciones consideradas:** * **Opción A:** Laravel 11 (PHP 8.2) -- Ecosistema maduro, curva de aprendizaje media; requiere configuración adicional de JWT.
  * **Opción B:** ASP.NET Core 8 (C#) -- Alto rendimiento; curva de aprendizaje pronunciada para el equipo.
  * **Opción C:** Spring Boot 3 (Java 21) -- Spring Security nativo para JWT, Spring Data JPA integrado, springdoc-openapi para Swagger.
* **Decisión:** Se eligió **Spring Boot 3 con Java 21**. Ofrece un escosistema maduro: Spring Security para la implementación directa de JWT y roles, Spring Data JPA que elimina el SQL concatenado (requisito explícito del PFC), y documentación automática con springdoc-openapi. El equipo cuenta con conocimiento previo de Java desde cursos anteriores.
* **Consecuencias positivas:**
  * Seguridad empresarial out-of-the-box (Spring Security).
  * ORM robusto (Hibernate 6) con validación de entidades.
  * Swagger UI generado automáticamente; cero configuración manual.
  * Transaccionalidad segura con anotación `@Transactional`.
* **Consecuencias negativas:**
  * Curva de aprendizaje en filtros de Spring Security y configuración de CORS. Se mitiga con documentación oficial y tutoriales de Baeldung.
  * Mayor tiempo de arranque (*cold start*) respecto a frameworks interpretados. Aceptable para el contexto universitario.

> Nota de versión: el proyecto corre hoy sobre Spring Boot 4.0.6 (ver
> `backend-springboot/pom.xml`); la decisión original evaluó la serie 3,
> vigente al momento de esta ADR, y se actualizó de forma natural en el
> tiempo sin que cambiara ninguna de las razones de la elección.
>
>
> Nota sobre la guía de la Entrega Final: la guía oficial de la Entrega
> Final especifica Spring Boot en su serie 3.2.x. El equipo evaluó realizar
> un downgrade de 4.0.6 a 3.2.x y decidió NO hacerlo, por las siguientes
> razones: (1) toda la suite de 268 pruebas backend, incluyendo el módulo
> de seguridad completo (filtros JWT, rate limiting, DaoAuthenticationProvider
> con inyección por constructor, anotación @MockitoBean en reemplazo de
> @MockBean), está escrita contra APIs específicas de Spring Boot 4.x que
> cambiaron respecto a la serie 3.x; (2) un downgrade a días del cierre de
> la Entrega Final introduce un riesgo de regresión alto sobre un sistema
> que hoy pasa el 100% de sus pruebas y tiene cobertura JaCoCo verificada,
> a cambio de un beneficio bajo (alinear un número de versión); (3) las
> razones técnicas originales de esta ADR (Spring Security nativo para JWT,
> Spring Data JPA, springdoc-openapi) siguen siendo válidas en la serie 4.x
> sin cambios de fondo. El equipo asume esta desviación de la guía como una
> decisión documentada y trazable, no como un descuido.

## Frontend: Angular 21

* **Contexto:** Se necesita un framework SPA con manejo robusto de formularios reactivos (validaciones de negocio en préstamos/multas), tipado estático que reduzca errores de integración contra el contrato REST del backend, e inyección de dependencias madura para servicios (auth, interceptors HTTP).
* **Opciones consideradas:**
  * **React 18** -- Ecosistema enorme, pero sin framework de formularios ni HTTP client oficial: requiere ensamblar librerías de terceros (elección adicional no trivial para un equipo de 3).
  * **Vue 3** -- Curva de aprendizaje baja, pero ecosistema de tipado estricto (TypeScript end-to-end) menos maduro que Angular en 2026.
  * **Angular 21** -- TypeScript de primera clase, `HttpClient` e interceptors oficiales (usados para adjuntar el `accessToken`, ver `jwt.interceptor.ts`), formularios reactivos (`ReactiveFormsModule`) con validadores síncronos/asíncronos nativos.
* **Decisión:** Se eligió **Angular 21**. El tipado estático end-to-end (interfaces TypeScript que reflejan los DTOs de Spring) reduce errores de contrato entre frontend y backend, y el sistema de interceptors resuelve de forma nativa el manejo del `accessToken` en memoria (ver `adr-010-autenticacion-jwt-rbac.md` para la decisión de por qué JWT en primer lugar).
* **Consecuencias positivas:** contrato de API auto-documentado en el propio TypeScript; formularios reactivos reducen bugs de validación duplicada cliente/servidor.
* **Consecuencias negativas:** curva de aprendizaje de RxJS/Observables para quien no la conocía (mitigado con la guía oficial de Angular); bundle inicial más pesado que una SPA minimalista, aceptable para el volumen de usuarios del proyecto.

## Base de datos: PostgreSQL 16

Justificación de alto nivel — el análisis completo de alternativas
(MySQL 8, MongoDB) con sus trade-offs específicos para este dominio vive
en **`adr-011-gestor-base-datos.md`**, para no duplicar el mismo
contenido en dos archivos. En resumen: el dominio (préstamos, reservas,
multas) es intrínsecamente relacional con integridad referencial
estricta, y el proyecto ya diseñó aislamiento de datos por rol vía Row
Level Security (`db/roles-privilegios.sql`, sección 7) — una capacidad
nativa de PostgreSQL que ninguna de las alternativas evaluadas iguala.

## Cache y blacklist de tokens: Redis 7

Justificación de alto nivel — el porqué de Redis como capa de cache de
aplicación en general (separado de su uso como blacklist de JWT) se
documenta en la sección "Contexto ampliado" de
**`adr-008-ttl-cache-libros.md`**, para mantener junto el razonamiento de
cache con su implementación (TTL) en un solo archivo. En resumen: mismo
motor ya presente en la pila por la decisión de blacklist de JWT
(`ADR-003-jwt-redis.md`), estructura de datos en memoria adecuada para
cache de lectura frecuente (catálogo de libros) sin justificar una
segunda pieza de infraestructura.

## Orquestación: Docker Compose

Justificación de alto nivel — el análisis completo de alternativas
(Kubernetes, despliegue manual) vive en
**`adr-007-estrategia-despliegue.md`**. En resumen: Docker Compose da
reproducibilidad de un solo comando (`make up`) para los 4 servicios
reales del stack (frontend, backend, PostgreSQL, Redis) sin la
complejidad operativa de un orquestador pensado para escalado
multi-nodo que este proyecto no necesita.