# Checklist — Principios FAIR (Findable, Accessible, Interoperable, Reusable)

**Proyecto**: SGB-SaaS — Entrega Final (Bloque E de la guía).
**Estándar evaluado**: los 15 subcriterios oficiales de los principios
FAIR (Wilkinson et al., 2016; texto verbatim de cada subcriterio tomado
de la página oficial [go-fair.org/fair-principles](https://www.go-fair.org/fair-principles/)
para esta tarea, no aproximado de memoria).
**Fecha**: 2026-08-13. **Commit base**: `6696bf1`.

## Alcance -- dos paquetes distintos, evaluados por separado cuando difieren

Este checklist distingue explícitamente entre dos paquetes con madurez
FAIR muy distinta, porque tratarlos como uno solo escondería la
diferencia real entre ambos:

- **Software** (el código fuente de SGB-SaaS): tiene un DOI de Zenodo
  vigente y correcto -- `10.5281/zenodo.22636466`, depósito real
  generado el 2026-09-07 sobre el tag final `v1.0.0`, vinculado por
  Zenodo como nueva versión del mismo registro que `v0.9.0-rc`
  (`10.5281/zenodo.21712467`, Tercera Entrega). El gap de
  desactualización que existió durante el desarrollo de esta entrega
  quedó documentado como `OBS-05` en
  `docs/observaciones/OBSERVACIONES.md`, y ya está cerrado -- ver
  portada del informe (`docs/capitulos/00-portada.tex`).
- **Datos** (las mediciones empíricas crudas en `docs/mediciones/`: JSON
  de k6, XML/CSV/HTML de JaCoCo, JSON de Lighthouse, CSV de la matriz de
  trazabilidad): **no tiene un DOI propio independiente** -- decisión
  deliberada del equipo de cubrirlo bajo el mismo DOI del software (ver
  `docs/capitulos/13-declaraciones.tex`, §13.2), no un gap pendiente.

**Leyenda**: ✅ cumple · ⚠️ cumple parcialmente (ver nota) · ❌ no cumple

## F -- Localizable (*Findable*)

| # | Ítem oficial (traducción fiel) | Estado | Evidencia / nota |
|---|---|---|---|
| F1 | (Meta)datos tienen asignado un identificador persistente y globalmente único | ✅ | **Software**: sí -- DOI `10.5281/zenodo.22636466`, depósito real de Zenodo generado el 2026-09-07 sobre el tag `v1.0.0` (vinculado como nueva versión del mismo registro que `v0.9.0-rc`, `10.5281/zenodo.21712467`; ver portada y `docs/capitulos/13-declaraciones.tex` §13.1). **Datos**: no tiene DOI propio independiente -- se cubre deliberadamente bajo el mismo DOI del software (mismo repositorio, misma licencia MIT, ver §13.2 del mismo capítulo), no es un placeholder pendiente. |
| F2 | Los datos están descritos con metadatos ricos | ✅ | `CITATION.cff` (formato Citation File Format 1.2.0, validado localmente con `cffconvert --validate` según su propio comentario de cabecera): autores con ORCID, afiliación, licencia, palabras clave, repositorio, DOI, versión. Cumple bien para el software; no existe un archivo de metadatos equivalente y dedicado para el paquete de datos de `docs/mediciones/` (cada archivo de medición documenta su propio contexto en prosa -- commit base, fecha, herramienta -- pero no en un formato de metadatos estructurado y agregado). |
| F3 | Los metadatos incluyen clara y explícitamente el identificador de los datos que describen | ✅ | **Software**: `CITATION.cff` incluye su propio campo `doi:` apuntando al registro de Zenodo vigente (`10.5281/zenodo.22636466`) -- cumple. **Datos**: no tienen un identificador propio distinto del DOI del software (ver F1) -- consecuencia directa de la decisión de no crear un archivado de datos separado, no de un gap pendiente. |
| F4 | (Meta)datos están registrados o indexados en un recurso de búsqueda | ⚠️ | **Software**: sí -- Zenodo indexa automáticamente los depósitos con DOI en agregadores como OpenAIRE y DataCite; el propio repositorio de GitHub es indexado por buscadores de código. **Datos**: solo indexados como parte del repositorio de GitHub general (búsqueda de código/archivos), no en un registro de datos de investigación dedicado (p. ej. un repositorio de datos institucional o un registro tipo re3data) -- indexación parcial, no la de un dataset publicado formalmente. |

## A -- Accesible (*Accessible*)

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| A1 | (Meta)datos son recuperables por su identificador usando un protocolo de comunicación estandarizado | ✅ | El DOI del software resuelve vía `https://doi.org/10.5281/zenodo.22636466` (protocolo HTTPS estándar); el repositorio completo (código y datos) es recuperable vía Git/HTTPS en `https://github.com/mloorm14/sgb-saas`. |
| A1.1 | El protocolo es abierto, gratuito y universalmente implementable | ✅ | HTTPS y Git son ambos protocolos abiertos, gratuitos y con implementaciones universales -- no hay dependencia de un cliente propietario. |
| A1.2 | El protocolo permite un procedimiento de autenticación y autorización, cuando es necesario | ✅ | GitHub soporta autenticación/autorización nativa (tokens, SSH) para repositorios privados; no se usa hoy porque el repositorio es público por diseño, pero el mecanismo existe y está disponible si el equipo decidiera restringir acceso en el futuro. |
| A2 | Los metadatos son accesibles incluso cuando los datos ya no están disponibles | ✅ | El registro de metadatos de Zenodo para el DOI del software (`10.5281/zenodo.22636466`, tag `v1.0.0`) persiste independientemente del estado del repositorio de GitHub (garantía propia de la infraestructura de Zenodo), y ya corresponde a la versión final de esta entrega, no a una desactualizada -- la versión anterior (`v0.9.0-rc`, `10.5281/zenodo.21712467`) queda accesible también, como versión previa del mismo registro. |

## I -- Interoperable (*Interoperable*)

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| I1 | (Meta)datos usan un lenguaje formal, accesible, compartido y ampliamente aplicable para la representación del conocimiento | ✅ | Los datos crudos usan formatos abiertos y ampliamente estandarizados: JSON (salidas de k6, resúmenes de Lighthouse), CSV (matriz de trazabilidad, `docs/trazabilidad/matriz.csv`), XML (reporte JaCoCo), HTML (reporte JaCoCo navegable), YAML (`CITATION.cff`, formato CFF 1.2.0). Ninguno es un formato propietario o cerrado. |
| I2 | (Meta)datos usan vocabularios que a su vez siguen los principios FAIR | ⚠️ | `CITATION.cff` sigue el esquema formal del propio estándar Citation File Format, que es en sí mismo un vocabulario FAIR reconocido por GitHub/Zenodo/Software Heritage. Los formatos de datos crudos (JSON/CSV/XML), en cambio, siguen esquemas propios de cada herramienta (k6, JaCoCo, Lighthouse) -- estructurados y documentados, pero sin un vocabulario controlado compartido a nivel de dominio (p. ej. no se usa un esquema tipo DCAT o schema.org para describir el conjunto de datos como tal). |
| I3 | (Meta)datos incluyen referencias calificadas a otros (meta)datos | ⚠️ | `CITATION.cff` referencia el repositorio de código (`repository-code`) y su propio DOI, pero no hace referencias cruzadas explícitas y calificadas a otros conjuntos de datos relacionados (p. ej. no enlaza formalmente hacia el DOI del dataset de mediciones, que de todas formas todavía no existe). Cada archivo individual de `docs/mediciones/` sí cita su commit base y archivos relacionados en prosa, pero no como una referencia de metadatos formal y máquina-legible. |

## R -- Reutilizable (*Reusable*)

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| R1 | (Meta)datos están descritos ricamente con una pluralidad de atributos precisos y relevantes | ⚠️ | `CITATION.cff` cubre bien el software (autores, ORCID, licencia, versión, DOI, palabras clave). El conjunto de datos de mediciones no tiene un archivo de metadatos agregado equivalente (p. ej. no hay un `docs/mediciones/METADATA.yml` con unidades de medida, instrumento usado, fecha de recolección por archivo) -- la información existe, pero dispersa en prosa dentro de cada reporte individual, no consolidada como metadatos estructurados. |
| R1.1 | (Meta)datos se publican con una licencia de uso de datos clara y accesible | ✅ | Licencia MIT en la raíz del repositorio (`LICENSE`), confirmada en `docs/capitulos/13-declaraciones.tex` §13.1-§13.2 como aplicable tanto al código como, por no excluirse explícitamente, a los datos de `docs/mediciones/` -- con la salvedad, ya declarada en ese mismo capítulo, de que el equipo no ha formalizado todavía una licencia de datos separada (p. ej. CC-BY) si en el futuro decide que es más apropiada que MIT para el dataset específicamente. |
| R1.2 | (Meta)datos están asociados con procedencia detallada | ✅ | El historial completo de Git (autoría de cada commit, fecha, mensaje) constituye procedencia real y verificable; además, cada archivo de `docs/mediciones/` documenta explícitamente su commit base y fecha de generación (mismo patrón usado en todo el proyecto, ver p. ej. `docs/checklists/incose2023-req.md`). |
| R1.3 | (Meta)datos cumplen con estándares relevantes de la comunidad de dominio | ⚠️ | `CITATION.cff` es un estándar real y activamente adoptado por la comunidad de software de investigación (reconocido nativamente por GitHub y Zenodo) -- cumple bien para el software. Los formatos de datos crudos (JSON de k6, XML de JaCoCo) son estándares nativos de sus respectivas herramientas, no estándares de una comunidad de datos de investigación más amplia (p. ej. no se depositaron siguiendo un esquema DDI o DataCite específico para conjuntos de datos de ingeniería de software empírica). |

## Resumen de cumplimiento

| Categoría | ✅ Cumple | ⚠️ Parcial | ❌ No cumple |
|---|---|---|---|
| Findable (F1-F4) | 3 | 1 | 0 |
| Accessible (A1, A1.1, A1.2, A2) | 4 | 0 | 0 |
| Interoperable (I1-I3) | 1 | 2 | 0 |
| Reusable (R1, R1.1-R1.3) | 2 | 2 | 0 |
| **Total (15 subcriterios)** | **10** | **5** | **0** |

**Lectura honesta del resultado (actualizada al cierre de v1.0.0).**
Ningún subcriterio se marca como "no cumple" porque, en sentido
estricto, ninguno está completamente ausente. Al momento de la primera
versión de este checklist (2026-08-13, commit base `6696bf1`), 8 de los
15 subcriterios eran parciales, con una causa raíz compartida: el DOI
del software apuntaba a una versión desactualizada (`v0.9.0-rc`,
`OBS-05`), pendiente de re-archivado al tag final. Ese gap ya se cerró
-- el DOI vigente (`10.5281/zenodo.22636466`) corresponde al tag
`v1.0.0` real, generado el 2026-09-07 -- por lo que los 3 subcriterios
que dependían de él (F1, F3, A2) pasan de parcial a cumplido, dejando
**5 de los 15 como parciales**. Los 5 restantes comparten una causa raíz
distinta y sigue siendo un gap real, no oculto: el paquete de datos de
mediciones empíricas no tiene su propio DOI ni un archivo de metadatos
agregado, aunque los datos en sí ya están versionados, en formato
abierto, con licencia clara y con procedencia verificable vía Git. Esto
es, como ya se declara en `docs/capitulos/13-declaraciones.tex` §13.2,
una decisión deliberada del equipo (cubrir los datos bajo el DOI del
software en vez de archivarlos por separado), no un gap pendiente de
resolución.
