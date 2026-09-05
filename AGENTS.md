# REGLA CRÍTICA DEL SISTEMA: NAVEGACIÓN OBLIGATORIA POR GRAFO (GRAPHIFY MCP)

Para CUALQUIER tarea de inspección, refactorización, corrección de errores o edición de código:
- QUEDA ESTRICTAMENTE PROHIBIDO realizar lecturas masivas de archivos (`read_file`), inspeccionar directorios completos o hacer búsquedas globales de texto (`grep`) como método inicial.
- ES OBLIGATORIO consultar primero el grafo AST local usando la herramienta MCP `graphify` (`affected`, `query` o `path`) para identificar las dependencias exactas.
- El agente SOLO debe leer e inspeccionar los archivos específicos reportados por el grafo como nodos relacionados.
- Toda verificación de estado parte del grafo; el `grep` directo solo se admite como fallback cuando el grafo no cubra el caso.

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

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## REGLA MANDATORIA DE ECONOMÍA DE TOKENS (Graphify AST)

Antes de realizar cualquier lectura masiva de archivos, búsquedas globales (grep) o modificaciones en componentes de Angular o servicios de Spring Boot:

- Consultá siempre el grafo de dependencias local con `graphify affected <NombreClaseOArchivo>` o mediante las herramientas del servidor MCP de Graphify.
- Identificá con precisión los nodos impactados y leé únicamente los archivos estrictamente necesarios.

## REGLAS DE USO PARA PLAYWRIGHT MCP (QA / E2E TESTING)

1. **ACTIVACIÓN EXCLUSIVA BAJO DEMANDA:**
   - QUEDA PROHIBIDO usar las herramientas de Playwright durante tareas de refactorización o corrección de código estándar. Solo activa el navegador si el usuario solicita explícitamente: "prueba la interfaz", "valida el flujo E2E" o "crea una prueba de Playwright".

2. **REQUISITO PREVIO Y TOLERANCIA A COLD-START (RENDER):**
   - Antes de ejecutar cualquier prueba de Playwright, verifica la conexión con la URL objetivo en Render.
   - Al hacer la petición inicial, contempla un tiempo de espera (timeout) de hasta 60 segundos en el navegador para permitir que la instancia de Render despierte si estaba suspendida. Si la URL no responde tras este tiempo, informa al usuario y detén la ejecución.

3. **EFICIENCIA DE CONTEXTO (OPTIMIZACIÓN DE TOKENS):**
   - Prioriza la inspección del árbol de accesibilidad o elementos DOM específicos. Evita solicitar capturas de pantalla (`screenshot`) a menos que sea estrictamente necesario para diagnosticar un error visual.
