# Instrucciones del agente — SGB-SaaS

## Skills del proyecto

Tenés 10 skills especializadas en `.opencode/skills/`. SIEMPRE revisá el catálogo
de skills disponibles antes de empezar cualquier tarea. Si la tarea coincide con
la descripción de una skill, cargá el SKILL.md completo antes de escribir código.

Skills disponibles:

| Skill | Activala cuando... |
|-------|-------------------|
| `sgb-backend-conventions` | Creás o editás controllers, services, repositories, entidades en `backend-springboot/` |
| `sgb-frontend-conventions` | Creás o editás componentes Angular, rutas, formularios en `frontend-angular/` |
| `sgb-workflow` | Empezás una tarea nueva, hacés commits, o necesitás saber el flujo Git |
| `sgb-testing` | Creás o editás archivos `*Test.java` o `*.spec.ts` |
| `sgb-dashboard-shells` | Agregás pantallas a un shell de dashboard o modificás sidebar/rutas por rol |
| `sgb-external-api-proxy` | Integrás con APIs externas (Google Books, Gemini, SMTP) o agregás proxy backend |
| `sgb-portada-imagenes` | Manipulás imágenes de portada de libros (upload, visualización, eliminación) |
| `sgb-git-troubleshooting` | Encontrás un error de Git (CRLF, auth, merge conflict, push fallido) |
| `sgb-tailwind-design-system` | Estilizás componentes con Tailwind (colores, tipografía, dark mode) |
| `sgb-flyway-migrations` | Creás o modificás migraciones SQL, stored procedures, o seed data |

## Reglas generales

- Nunca inventes contratos (DTOs, endpoints, roles, nombres de tablas) — leé el código real.
- Seguí las convenciones del proyecto: Angular standalone, `@if`/`@for`, Tailwind con tokens, conventional commits.
- Al terminar una tarea, reportá: archivos editados, decisiones tomadas, TOTAL de tests, y TODOs pendientes.
