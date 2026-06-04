# ADR-001: Selección del framework backend del servidor

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