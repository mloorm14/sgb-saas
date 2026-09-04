# Procedencia de datos — tablas y figuras del informe académico

Traza cada tabla y figura con datos cuantitativos citada en
`docs/capitulos/*.tex` hacia el archivo crudo que la origina, el script
que la produce (si existe uno versionado), y el commit en que se generó
por última vez. Requisito R2 de la guía. Complementa a
`docs/mediciones/DATA-DICTIONARY.md` (qué campos tiene cada archivo
crudo) sin repetirlo -- este archivo responde "de dónde viene", no "qué
forma tiene".

**Fecha**: 2026-08-31. **Commit base**: `c6de386`. **Método**: se
recorrieron los 13 capítulos de `docs/capitulos/*.tex` buscando
`\begin{table}`, `\begin{longtable}`, `\begin{tabularx}`,
`\begin{tabular}` e `\includegraphics` (no solo los que tienen
`\label{tab:...}`/`\label{fig:...}` -- 3 de las 15 filas de abajo
corresponden a tablas reales sin `\label`, encontradas igual). El commit
de cada archivo crudo se obtuvo con `git log -1 -- <archivo>` (o
`git log -1 -L <líneas>:<archivo>` cuando hacía falta precisar qué
commit tocó una tabla específica dentro de un capítulo con más de un
commit en su historial), no se transcribió de memoria ni se asumió.

**Columna "Script"**: varias tablas se calcularon con un conteo manual
verificado (p. ej. `python3` con `csv.DictReader` ejecutado durante la
tarea correspondiente, no guardado como script versionado en `scripts/`)
-- se declara así explícitamente en vez de sugerir que existe un script
reproducible con un nombre de archivo cuando en realidad no lo hay.

| # | Tabla / Figura | Capítulo | Archivo(s) crudo(s) de origen | Script | Commit |
|---|---|---|---|---|---|
| 1 | `tab:res-perf-descriptivo` (estadística descriptiva de rendimiento) | 08-resultados.tex, §Rendimiento | `docs/mediciones/perf/k6-run1.json` … `k6-run5.json` | `scripts/perf-analysis.py` (bootstrap 2000 réplicas, semilla `BOOTSTRAP_SEED = 42`) | Datos: `ea149cc` (run 1) → `06a3470` (run 5 + script). Tabla en el capítulo: `993b5e7`. |
| 2 | `fig:res-perf-comparacion` (`p95-comparacion-escenarios.pdf`) | 08-resultados.tex, §Rendimiento | `docs/mediciones/perf/k6-run1.json` … `k6-run5.json` | `scripts/perf-analysis.py` genera el `.svg`; conversión a `.pdf` con `svglib`/`reportlab` fue un paso manual de esta redacción, **no** un script versionado en `scripts/`. | `.svg`: `06a3470`. `.pdf` + inclusión en el capítulo: `993b5e7`. |
| 3 | `tab:res-owasp` (6 controles OWASP auditados) | 08-resultados.tex, §Seguridad | Los 16 archivos de `docs/mediciones/sec/*.md` (evidencia manual vía `curl` contra el stack Docker real) | Sin script de agregación automática de los 16 en una tabla -- `scripts/owasp-audit.sh` (`make audit`) solo re-verifica 4 de los 6 controles (A01/A03/A07/A09), no agrega la tabla del informe. | Evidencia manual original: varios commits entre `6d41b88` y `41407b2` (2026-07-30 a 2026-08-10). Re-verificación automatizada: `28928ae` (A01/A03/A07/A09) y `04ce7c5` (A05, más reciente en el directorio). Tabla en el capítulo: `993b5e7`. |
| 4 | `tab:res-jacoco` (cobertura antes/después) | 08-resultados.tex, §Cobertura | `docs/mediciones/jacoco/report.xml`, `report.csv` | `jacoco-maven-plugin` vía `./mvnw clean verify` (Maven, no un script propio del repositorio) | Reporte: `3fce7e7`. Tabla en el capítulo: `993b5e7`. |
| 5 | `tab:res-lighthouse` (2 corridas móvil) | 08-resultados.tex, §Calidad web | `docs/mediciones/lighthouse/lhci-20260731-0300.json` (corrida 1), `lhci-20260731-0330.json` (corrida 2) | `@lhci/cli` vía `frontend-angular/lighthouserc.js` (herramienta de terceros, no script propio) | Corrida 1: `c4ef133`. Corrida 2 (post-fix SEO): `51607f3`. Tabla en el capítulo: `993b5e7`. |
| 6 | `tab:res-resumen` (resumen de los 5 bloques) | 08-resultados.tex, §Resumen | Deriva de las filas 1, 3, 4 y 5 de esta tabla (sin archivo crudo propio -- es una síntesis, no una nueva medición) | N/A | `993b5e7`. |
| 7 | `tab:trabajos-comparativa` (10 trabajos primarios) | 03-trabajos-relacionados.tex | `docs/bibliografia.bib` (34 referencias, verificadas contra Crossref) + resúmenes indexados de cada trabajo consultados directamente (no descargados como archivo al repositorio) | Sin script -- síntesis narrativa manual, declarada así en el propio capítulo (§Estrategia de búsqueda). | `26f4778`. |
| 8 | `fig:prisma-flow` (diagrama de flujo de selección) | 03-trabajos-relacionados.tex | Mismo acervo que la fila anterior; los conteos de cada etapa (15→15→15→15→10) están documentados en prosa en el propio capítulo, no en un archivo de datos separado | Sin script -- conteo manual. | `26f4778`. |
| 9 | Tabla de mapeo ADR ↔ 6 temas obligatorios del Bloque D (sin `\label`, §Decisiones arquitectónicas documentadas) | 06-diseno-arquitectura.tex | Los 13 archivos de `docs/adr/*.md` (y su índice `docs/adr/README.md`) | Sin script -- mapeo manual, documentado también en `docs/adr/README.md`. | ADRs: `a0a2aa8` (última aclaración de numeración). Tabla en el capítulo: `3538f13`. |
| 10 | Tabla de atributos de calidad ISO/IEC 25010 (sin `\label`, §Atributos de calidad) | 06-diseno-arquitectura.tex | `docs/arquitectura/ISO25010.md` (reproducida verbatim según declara el propio capítulo, con una nota al pie que corrige la cifra desactualizada de "6 ADRs" del archivo fuente) | Sin script -- documento fuente redactado manualmente, con las cifras de rendimiento tomadas de la fila 1 de esta tabla. | Fuente (`ISO25010.md`): `e90c39b`. Tabla en el capítulo: `3538f13`. |
| 11 | `tab:matriz-resumen` (distribución por `tipo_acceso`, §Matriz de trazabilidad, `sec:matriz-resumen`) | 06-diseno-arquitectura.tex | `docs/trazabilidad/matriz.csv` (43 filas) | Sin script versionado -- conteo manual verificado con `python3` (`csv.DictReader`) durante la tarea que corrigió esta tabla, no un script guardado en `scripts/`. | Fix del CSV (comas sin escapar en 4 filas): `ecfaf52`. Tabla recalculada en el capítulo: `fd68bba`. |
| 12 | Tabla de distribución MoSCoW / tipo / estado (sin `\label`, §Los 43 requisitos: categorización MoSCoW) | 09-ingenieria-requisitos.tex | `docs/trazabilidad/matriz.csv` | Sin script de agregación -- `scripts/validate-traceability.sh` valida el esquema/presencia de columnas (incl.\ `tipo_acceso`, `estado`), pero no calcula estas cuentas/porcentajes; conteo manual. | `3538f13`. |
| 13 | Cifra "100\,% de los `Must` verificados" (prosa, §Proceso de validación) | 09-ingenieria-requisitos.tex | `docs/trazabilidad/matriz.csv` (columna `estado`) | Sin script -- cierre logrado agregando 2 tests nuevos (`AuthControllerTest`, `AuthServiceTest`) que permitieron pasar `REQ-F-001`/`REQ-NF-013` de `implementado` a `verificado`. | Tests que cerraron el gap: `e1f0c25`. Prosa en el capítulo: `3538f13`. |
| 14 | Tasa de estabilidad "95,3\,%" (prosa, §Estabilidad del conjunto de requisitos) | 09-ingenieria-requisitos.tex | `docs/requisitos/CHANGELOG-REQ.md` (comparación `SRS-v0.9.0-rc.md` de 30 requisitos vs.\ `SRS-v1.0.0.md` de 43) | Sin script versionado en `scripts/` -- comparación cuerpo-completo (no solo título) hecha de forma ad-hoc para producir `CHANGELOG-REQ.md`, no repetible desde el repositorio tal cual hoy. | `CHANGELOG-REQ.md`: `82df169`. Prosa en el capítulo (incluida la corrección honesta de la cifra de "verificado" desactualizada del propio `CHANGELOG-REQ.md`, 48,8\,% → 53,5\,%): `3538f13`. |
| 15 | `tab:decl-credit` (distribución de roles CRediT) | 13-declaraciones.tex | Historial completo de `git log`/`git shortlog -sne --all` (recuento de commits por autor) + `docs/observaciones/OBSERVACIONES.md` (asignación de OBS-08 a Panamá) | Sin script versionado -- conteo manual de `git shortlog` ejecutado durante la redacción del capítulo, no un script guardado en `scripts/`. | `6696bf1` (confirmación final de la tabla, tras la revisión del equipo). |
| 16 | `fig:sus-boxplot` (boxplot de puntajes SUS, `sus_boxplot.svg` y `.png`) — RETIRADA (bloque en $N=0$, ver `OBS-08` reabierta) | Resultados, §Usabilidad (pendiente) | `docs/mediciones/sus/sus.csv` (datos mock, solo validación del pipeline) | `scripts/sus-analysis.ipynb` (celda 7: boxplot con umbral 68, paleta colorblind) | Sin commit de datos reales; figura generada sobre mock. |
| 17 | `fig:sus-items` (desglose por ítem SUS, `sus_items_breakdown.svg`) — RETIRADA (bloque en $N=0$, ver `OBS-08` reabierta) | Resultados, §Usabilidad (pendiente) | `docs/mediciones/sus/sus.csv` (datos mock, solo validación del pipeline) | `scripts/sus-analysis.ipynb` (celda 8: barras horizontales Q1–Q10, Likert 1–5) | Sin commit de datos reales; figura generada sobre mock. |
| 18 | `tab:sus-descriptivo` (estadística descriptiva SUS) — RETIRADA (bloque en $N=0$, ver `OBS-08` reabierta) | Resultados, §Usabilidad (pendiente) | Sin datos reales todavía; el método (media, mediana, DT, IC 95% con `scipy.stats.t.interval`, Bangor) queda implementado en `scripts/sus-analysis.ipynb` (celdas 3–4) para la corrida futura | Pipeline verificado contra mock | Sin commit de datos reales. |

## Notas de honestidad sobre este archivo

- **Ninguna fila usa un script que no exista de verdad.** Donde la
  columna "Script" dice "sin script", se verificó primero que no hubiera
  uno versionado en `scripts/` para esa tabla específica (búsqueda
  directa en el directorio, no una suposición) -- son conteos manuales
  reales, no automatizados, y se declaran como tales en vez de sugerir
  una reproducibilidad que no existe hoy.
- **`scripts/validate-traceability.sh` no calcula ninguna de las cifras
  citadas** en las filas 11 y 12 -- valida que las columnas de
  `matriz.csv` tengan el esquema y los valores permitidos correctos
  (incluye `tipo_acceso` en su lista de columnas validadas), pero no
  agrega conteos ni porcentajes. Confundir "el CSV pasa la validación de
  esquema" con "la tabla se generó automáticamente" habría sido
  inexacto.
- **Las filas 3 y 8 no tienen un único archivo o commit "fuente"
  limpio** porque agregan evidencia de varios archivos generados en
  fechas distintas (16 archivos OWASP a lo largo de 3 semanas; el acervo
  bibliográfico de 34 referencias construido en dos tandas) -- se citan
  los commits más relevantes de cada extremo del rango en vez de forzar
  un solo commit que no representaría el proceso real.
- Esta tabla no incluye las tablas puramente cualitativas sin datos
  cuantitativos que trazar a un archivo crudo (p.~ej. la tabla de
  autores/C.I./ORCID de la portada, `docs/capitulos/00-portada.tex`) --
  fuera del alcance de "tabla o figura con datos cuantitativos" que pide
  esta tarea.

## Referencias

- `docs/mediciones/DATA-DICTIONARY.md` (qué campos tiene cada archivo crudo)
- `docs/mediciones/README.md` (convención general de evidencia)
- `docs/trazabilidad/matriz.csv`, `scripts/validate-traceability.sh`
