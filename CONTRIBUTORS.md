# Contribuidores — SGB-SaaS

Roles asignados según la taxonomía [CRediT](https://credit.niso.org/) (14
roles estándar). Este archivo se basa en el **trabajo real commiteado
hasta la fecha** (ver `git log`), no en el rol nominal de cada integrante
en el equipo — un rol CRediT solo se asigna si hay evidencia concreta de
que esa persona lo ejerció. Entrega Final.

## Conteo real de commits (identidades unificadas)

Hay múltiples identidades Git por persona (variaciones de nombre/email
por distintas máquinas/configuraciones). Para que la auditoría sea
reproducible y no dependa de una suma manual, el repositorio incluye
`.mailmap` con la unificación explícita de alias conocidos; el conteo se
recalcula con `git -c log.mailmap=true shortlog -sne --no-merges HEAD`.

| Persona | Alias Git considerados | Commits |
|---|---|---|
| Irvin Cajas Ibarra | `Theirvin1 <icajasi@msuteq.edu.ec>`, `TheIrvin <irvincajas72@gmail.com>`, `Irvin <icajasi@msuteq.edu.ec>`, `Irvin Cajas <icajasi@msuteq.edu.ec>` | **711** |
| Marlon Loor Medranda | `Marlon Loor <mloorm14@uteq.edu.ec>`, `mloorm14 <mloorm14@uteq.edu.ec>`, `Loor Marlon <mloorm14@uteq.edu.ec>`, `Marlon Taylor Loor Medranda <mloorm14@uteq.edu.ec>` | **326** |
| Moises Panama Murillo | `MoisesPanama <mpanamam@uteq.edu.ec>`, `Moisés Panamá <mpanamam@uteq.edu.ec>`, `Moises Panama <mpanamam@uteq.edu.ec>` | **291** |

711+326+291 = 1328, más 1 de `TeilorSuit` y 1 del bot = 1330. Para
re-sincronizar al cierre: `git rev-list --count --no-merges <rev>` y
`git -c log.mailmap=true shortlog -sne --no-merges <rev>`.

Excluidos del conteo de autoría humana: `copilot-swe-agent[bot]` (1 commit,
agente automatizado, no es una persona del equipo). El commit
`d9e7c08` (`TeilorSuit <0988011232m@gmail.com>`, 9-jun-2026, esqueleto
base de Angular) **no se asigna a ninguno de los 3 integrantes**: la
identidad no coincide con ningún alias conocido y el contenido del
commit (archivos vacíos + `.gitignore`) no permite atribuirlo con
certeza — se deja documentado aquí en vez de forzar una asignación.

## Marlon Loor Medranda (Loor Medranda, Marlon Taylor)

Rol nominal en el equipo: Tech Lead / DevOps / Seguridad.

- **Conceptualization** — formulación del alcance y la arquitectura del
  sistema: estrategia de ramas y versionado, decisiones de stack y
  despliegue (ADR-001, ADR-007/013, OBS-11/OBS-18), definición de los
  módulos y su secuencia de construcción.
- **Software** — implementación de autenticación JWT+RBAC, cookies
  HttpOnly, blacklist de tokens en Redis, cache del catálogo con TTL
  externo, corrección de `GlobalExceptionHandler`/RFC 7807, pinning de
  imágenes Docker por digest, scripts de build (`build-init-sql.sh`,
  `mediciones-header.sh`).
- **Project administration** — coordinación de la estrategia de ramas,
  ADRs, esquema de versionado y estructura de `docs/`.
- **Supervision** — revisión y corrección de hallazgos de QA sobre
  trabajo de otros integrantes (ver commits `00ecff4`, `1c30b2e`, entre
  otros).
- **Validation** — verificación en vivo de cada cambio de seguridad/cache
  contra el stack Docker real, documentada en `docs/mediciones/sec/`.
- **Data curation** — creación y mantenimiento de
  `docs/mediciones/DATA-DICTIONARY.md` y `docs/mediciones/DATA-PROVENANCE.md`;
  las referencias históricas a hashes no alcanzables tras la reescritura
  del historial quedan declaradas como invalidadas, no reemplazadas por
  hashes inventados.
- **Formal analysis** — autoría exclusiva de `scripts/perf-analysis.py`
  (Wilcoxon pareado + Cliff's delta sobre p95 de k6); evaluación y
  documentación de por qué la corrección por comparaciones múltiples no
  aplica (m=1 comparación real en todo el documento).
- **Visualization** — exportación real de los diagramas C4 Nivel 1 y
  Nivel 2 desde `docs/arquitectura/workspace.dsl` y generación del
  diagrama entidad-relación real desde el esquema PostgreSQL
  reconstruido (`docs/diagramas/der-real.pdf`), reemplazando material
  desactualizado o incorrecto de entregas previas.
- **Writing – original draft** — redacción de la mayoría de los
  capítulos de `docs/capitulos/*.tex` (86 commits sobre esos archivos).
- **Writing – review & editing** — unificación de cifras de cobertura,
  corrección de referencias de commit rotas en `OBSERVACIONES.md`,
  incorporación de citas bibliográficas faltantes (Peffers et al. 2007,
  Hevner et al. 2004), índice de listados/siglas, referenciado de
  etiquetas huérfanas y reducción de desbordes de caja en la
  compilación LaTeX.

## Irvin Cajas Ibarra (Cajas Ibarra, Irvin Marcelo)

Rol nominal en el equipo: Backend (CRUD/Préstamos).

- **Software** — CRUD de libros y módulo completo de
  Préstamos/Reservas/Multas (backend); sistema de respaldos: entidades
  y repositorios de `BackupProgramacion`/`ConfiguracionRespaldo`,
  migraciones `V32`-`V38`, procedimientos almacenados multi-`OUT`
  (`db/procs`), y microservicio Node.js independiente para volcado
  completo vía `pg_dump` (`backup-service/src`, commit `126a7ee`).
- **Resources** — gestión de infraestructura de despliegue:
  `render.yaml`, `docker-compose.yml`, y hardening del microservicio de
  respaldos con `INTERNAL_API_KEY` (commit `8b8c4af`).
- **Validation** — cierre de hallazgos OWASP con evidencia real contra
  el stack Docker (reportes ZAP baseline y contra producción, ver
  commits bajo `docs/mediciones`), verificación en tiempo de ejecución
  de los procedimientos almacenados multi-`OUT` (ADR-013, commit
  `baa0820`), y evidencia E2E del flujo préstamo-devolución-multa.
- **Data curation** — organización de `docs/mediciones/sec/` con índice
  resumen (commit `4b7f50a`), creación del esqueleto inicial de
  respuestas SUS y su script de análisis (commit `1ad718b`), archivado
  de notebooks con outputs reales ejecutados.
- **Methodology** — autoría de ADR-012 (estrategia de producción),
  ADR-015 (TLS terminado en el proxy) y ADR-016 (privacidad del
  chatbot con Gemini); diseño del protocolo de reproducibilidad del
  notebook de análisis de rendimiento (invocación por subprocess de
  `scripts/perf-analysis.py` para no duplicar lógica estadística).
- **Investigation** — primer reporte de análisis estático de SQL
  (Bloque A.2.3, commit `b683847`).
- **Visualization** — creación de los primeros diagramas C4 y
  entidad-relación del proyecto (commit `2205566`, Entrega 1A).

## Moises Panama Murillo (Panama Murillo, Moises Antonio)

Rol nominal en el equipo: Frontend.

- **Software** — módulos Angular de autenticación (login/registro,
  guards, interceptor JWT) y CRUD de libros (frontend); módulo de
  auditoría completo (backend: endpoint de resumen por categorías,
  instrumentación de escritura en 6 servicios; frontend: vista de
  tarjetas con drill-down, filtros, paginación); rebranding completo de
  "SGB UTEQ" a "Leibri" (commit `02b3686`); infraestructura de modo
  oscuro integrada en la navegación y banners (commit `366cc40`).
- **Visualization** — 29 mockups HTML de referencia bajo
  `docs/mockups/` (dashboards, auditoría, configuración, credencial QR,
  chatbot); generación de los gráficos de SUS (`sus_boxplot.svg/png`,
  `sus_items_breakdown.svg`, paleta accesible a daltonismo), hash
  histórico invalidado.
- **Data curation** — creación del archivo SUS de validación
  (`docs/mediciones/sus/sus.csv`) y su posterior retiro declarado como
  evidencia empírica (N=0 en todo el
  entregable, ver OBS-08 reabierta y `DATA-PROVENANCE.md` filas 16-18:
  sin commit de datos reales), eliminación de
  15 PDFs con PII del historial de Git por cumplimiento de protección de
  datos, actualización de `ETHICS.md`, `DATA-DICTIONARY.md` (sección SUS,
  22 campos) y `DATA-PROVENANCE.md` (hash histórico invalidado por la
  reescritura del historial).
- **Formal analysis** — reescritura completa de
  `scripts/sus-analysis.ipynb`: estadística descriptiva, intervalo de
  confianza al 95% y desglose por ítem del instrumento SUS (evidencia
  versionada en el cuaderno; hash histórico invalidado por la reescritura
  del historial).
- **Validation** — evidencia E2E del frontend contra el backend real
  para préstamos/reservaciones/multas (commit `84ffc30`); corrección de
  brechas de accesibilidad WCAG AA (modales, objetivos táctiles, ARIA,
  manejo de tecla Escape; patrón ARIA combobox en 6 campos de
  autocompletado — commits `1640dd9`, `b1399a2`).

## Cobertura de los 14 roles CRediT

13 roles asignados arriba con evidencia (Software, Conceptualization,
Project administration, Supervision, Validation, Data curation, Formal
analysis, Visualization, Writing – original draft, Writing – review &
editing, Resources, Methodology, Investigation). El rol restante se
declara abajo para completar la taxonomía:

- **Funding acquisition** — **no aplica**: proyecto académico de fin de
  curso sin financiamiento externo ni convocatoria que lo requiera. Se
  declara explícitamente para que la taxonomía quede completa (14/14
  cubiertos entre asignados y declarados no aplicables) en vez de dejar
  un rol sin mencionar.

## Referencias

- CRediT Contributor Roles Taxonomy: https://credit.niso.org/
- `CITATION.cff` (afiliación y nombres completos en formato CFF)
- `git log --oneline` (evidencia de autoría de cada commit)
