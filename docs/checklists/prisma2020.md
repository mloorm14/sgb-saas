# Checklist — PRISMA 2020 (aplicado al Cap. 3, Trabajos Relacionados)

**Proyecto**: SGB-SaaS — Entrega Final (Bloque E de la guía).
**Estándar evaluado**: Page, M.J. et al. (2021). *The PRISMA 2020
statement: an updated guideline for reporting systematic reviews*. BMJ
2021;372:n71. Checklist oficial verificado contra el PDF publicado en
`prisma-statement.org` (27 ítems numerados, 42 filas contando subítems),
descargado directamente para esta tarea.
**Capítulo evaluado**: [`docs/capitulos/03-trabajos-relacionados.tex`](../capitulos/03-trabajos-relacionados.tex).
**Fecha**: 2026-08-13. **Commit base**: `6696bf1`.

## Advertencia de alcance -- léase antes de la tabla

El propio Cap. 3 declara, en su primer párrafo, que sigue "una
metodología **adaptada** de PRISMA~2020 -- adaptada porque [...] no es
una revisión sistemática completa ejecutada desde cero para este
capítulo, sino un acervo bibliográfico construido en dos momentos del
proyecto y consolidado aquí". PRISMA~2020 fue diseñado para revisiones
sistemáticas de literatura clínica/biomédica con síntesis cuantitativa
(meta-análisis), registro de protocolo pre-registrado y evaluación
formal de riesgo de sesgo por estudio -- ninguno de esos tres elementos
se ejecutó ni se pretende haber ejecutado en el Cap. 3. Este checklist
no fuerza el cumplimiento de esos ítems: se marcan explícitamente **N/A
-- revisión reducida de alcance de PFC, no revisión sistemática
completa**, siguiendo el criterio de honestidad ya establecido en el
propio capítulo, no un criterio inventado para este documento.

**Leyenda**: ✅ cumple · ⚠️ cumple parcialmente (ver nota) · ❌ no cumple ·
**N/A** no aplica (revisión reducida, no revisión sistemática completa)

## TÍTULO

| # | Ítem oficial (traducción fiel) | Estado | Evidencia / nota |
|---|---|---|---|
| 1 | Identificar el reporte como una revisión sistemática | N/A | El propio Cap. 3 declara explícitamente que **no** es una revisión sistemática completa (ver advertencia de alcance arriba) -- por diseño, el capítulo se titula "Trabajos relacionados", no "Revisión sistemática". Marcar este ítem como cumplido exigiría titular el capítulo de una forma que tergiversaría su alcance real; se prefiere la honestidad ya aplicada en el resto del documento sobre la letra estricta de este ítem. |

## RESUMEN

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 2 | Ver el checklist PRISMA 2020 para resúmenes (checklist separado, específico para el resumen de una publicación de revisión sistemática independiente) | N/A | El Cap. 3 es un capítulo dentro de una tesis, no una publicación de revisión sistemática independiente con resumen propio -- no existe un "abstract" separado de este capítulo al que aplicar ese checklist adicional. El Resumen general del informe (Cap. 1) no está dedicado a la revisión de literatura. |

## INTRODUCCIÓN

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 3 | Describir el fundamento (*rationale*) de la revisión en el contexto del conocimiento existente | ✅ | Párrafo introductorio del Cap. 3 (situar a SGB-SaaS frente a la literatura indexada) y, sobre todo, §Brecha identificada, que argumenta explícitamente el fundamento de por qué se revisó esta literatura. |
| 4 | Proveer una declaración explícita del/los objetivo(s) o pregunta(s) que aborda la revisión | ✅ | Primer párrafo del capítulo: "Este capítulo sitúa a SGB-SaaS frente a la literatura académica indexada disponible sobre sistemas de gestión bibliotecaria y sobre los patrones arquitectónicos y de proceso que el sistema efectivamente utiliza" -- declaración explícita de objetivo, aunque no formateada como pregunta de investigación (PICO u otro formato formal). |

## MÉTODOS

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 5 | Especificar los criterios de inclusión/exclusión y cómo se agruparon los estudios para las síntesis | ✅ | §Estrategia de búsqueda: "Criterios de inclusión" y "Criterios de exclusión" explícitos (4 criterios de inclusión, 2 de exclusión + 1 excepción declarada); agrupación explícita en Grupo A (15 refs., dominio bibliotecario comparable) y Grupo B (15 refs., apoyo metodológico) en §Proceso de selección. |
| 6 | Especificar todas las bases de datos, registros, sitios web, organizaciones, listas de referencias y otras fuentes consultadas, y la fecha de la última consulta de cada una | ⚠️ | Se listan las 5 fuentes (IEEE Xplore, ACM Digital Library, Scopus, ScienceDirect, SpringerLink) más Crossref/IEEE Xplore para la búsqueda dirigida de 2 referencias adicionales -- pero no se especifica una fecha exacta de última consulta por fuente para el acervo original de 27 referencias (descrito solo como "curadas por el equipo en fases anteriores del proyecto"). |
| 7 | Presentar las estrategias de búsqueda completas para todas las bases de datos, registros y sitios web, incluyendo filtros y límites usados | ⚠️ | Se presenta **una** cadena de búsqueda de referencia (booleana, con términos de dominio + patrones técnicos + tipo de evaluación) declarada como "la usada en las búsquedas originales del equipo y replicada en la búsqueda dirigida de este capítulo" -- pero no se documentan cadenas específicas por base de datos individual, ni filtros/límites de plataforma (p. ej. filtro de fecha aplicado directamente en la interfaz de Scopus) por separado. |
| 8 | Especificar los métodos usados para decidir si un estudio cumplía los criterios de inclusión, incluyendo cuántos revisores examinaron cada registro, si trabajaron de forma independiente, y detalles de herramientas de automatización si se usaron | ❌ | No se documenta en el Cap. 3 cuántos de los 3 integrantes del equipo revisaron cada referencia candidata, si el filtrado se hizo de forma independiente y luego se consolidó, o si lo hizo una sola persona. No se usaron herramientas de automatización de cribado. Gap real. |
| 9 | Especificar los métodos usados para recolectar datos de los reportes, incluyendo cuántos revisores, si trabajaron de forma independiente, y procesos para confirmar datos con los autores originales | ❌ | No documentado. La extracción de los datos de la tabla comparativa (`tab:trabajos-comparativa`) no describe cuántas personas la construyeron ni si hubo verificación cruzada entre revisores. |
| 10a | Listar y definir todos los resultados (*outcomes*) para los que se buscaron datos | ✅ | Las 8 columnas de `tab:trabajos-comparativa` (Año, Dominio, Pila tecnológica, Patrones arquitectónicos, Evaluación empírica reportada, Limitaciones declaradas, Diferencia frente a SGB-SaaS) constituyen, en la práctica, el conjunto definido y consistente de variables buscadas para cada uno de los 10 trabajos primarios. |
| 10b | Listar y definir todas las demás variables buscadas (p. ej. características de participantes/intervención, fuentes de financiamiento) y describir supuestos sobre información faltante o poco clara | ⚠️ | Cuando la información no pudo confirmarse a partir del resumen indexado disponible, el capítulo lo declara explícitamente ("no confirmado a partir del resumen indexado disponible", usado en varias filas de la tabla) -- eso cubre el manejo de datos faltantes. No se buscaron ni declararon, en cambio, variables como fuentes de financiamiento de los 10 estudios primarios (no aplicable de forma directa al tipo de comparación técnica realizada, pero tampoco se declara explícitamente por qué se omitió). |
| 11 | Especificar los métodos usados para evaluar el riesgo de sesgo en los estudios incluidos, incluyendo la(s) herramienta(s) usada(s) y cuántos revisores evaluaron cada estudio | ❌ | No se aplicó ninguna herramienta formal de evaluación de riesgo de sesgo (p. ej. un checklist tipo CASP adaptado a ingeniería de software) a los 10 trabajos primarios. Gap real, coherente con el hecho de que el propio capítulo se declara una revisión reducida, no sistemática completa. |
| 12 | Especificar, para cada resultado, la(s) medida(s) de efecto usada(s) en la síntesis o presentación de resultados | N/A | No se realizó una síntesis cuantitativa/meta-analítica con medidas de efecto agregadas (p. ej. razón de riesgo, diferencia de medias) -- la comparación es narrativa/cualitativa por diseño. |
| 13a | Describir los procesos usados para decidir qué estudios eran elegibles para cada síntesis | ✅ | El flujo de selección adaptado de PRISMA (`fig:prisma-flow`, con conteos reales: 15 identificados → 15 sin duplicados → 15 cribados → 15 evaluados a texto completo → 10 incluidos en la tabla comparativa) documenta exactamente este proceso, incluyendo el motivo de exclusión de los 5 no incluidos en la tabla primaria (revisiones/artículos de posición). |
| 13b | Describir métodos requeridos para preparar los datos para presentación o síntesis (p. ej. manejo de estadísticas resumen faltantes, conversiones de datos) | N/A | No aplica: no hay síntesis estadística de datos numéricos entre estudios que requiera conversión o imputación -- la síntesis es narrativa por diseño. |
| 13c | Describir métodos usados para tabular o mostrar visualmente los resultados de estudios individuales y síntesis | ✅ | `tab:trabajos-comparativa` (tabla `longtable` en modo apaisado, 10 filas × 8 columnas) y `fig:prisma-flow` (diagrama TikZ del proceso de selección). |
| 13d | Describir métodos usados para sintetizar resultados y dar una justificación de la(s) elección(es); si se hizo meta-análisis, describir el/los modelo(s), método(s) de heterogeneidad y software usado | ⚠️ | Se usa síntesis narrativa organizada por subsección temática (§Panorama de trabajos relacionados: sistemas bibliotecarios, ingeniería de requisitos, prácticas ágiles, diseño responsivo, seguridad de transporte, fundamentos de arquitectura) -- una elección de método razonable dado que no hay datos cuantitativos homogéneos entre los 10 trabajos para meta-analizar, pero esa justificación (por qué narrativa y no cuantitativa) no se declara de forma explícita en el propio capítulo. |
| 13e | Describir métodos usados para explorar posibles causas de heterogeneidad entre resultados de los estudios | N/A | No aplica -- no hay meta-análisis del que explorar heterogeneidad estadística. |
| 13f | Describir cualquier análisis de sensibilidad realizado para evaluar la robustez de los resultados sintetizados | N/A | No aplica, mismo motivo que 13e. |
| 14 | Describir métodos usados para evaluar el riesgo de sesgo debido a resultados faltantes en una síntesis (sesgo de publicación) | ❌ | No se evaluó formalmente sesgo de publicación (p. ej. mediante un gráfico de embudo o prueba estadística) en el acervo revisado. |
| 15 | Describir métodos usados para evaluar la certeza (confianza) en el cuerpo de evidencia para un resultado | N/A | No aplica -- la evaluación de certeza tipo GRADE es propia de síntesis de evidencia clínica/cuantitativa, no de una comparación narrativa de arquitecturas de software. |

## RESULTADOS

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 16a | Describir los resultados del proceso de búsqueda y selección, desde el número de registros identificados hasta el número de estudios incluidos, idealmente con un diagrama de flujo | ✅ | `fig:prisma-flow`, con conteos reales en cada etapa (15 → 15 → 15 → 15 → 10). |
| 16b | Citar los estudios que podrían parecer elegibles pero fueron excluidos, y explicar por qué | ✅ | El nodo de exclusión del propio diagrama (`fig:prisma-flow`) cita explícitamente los 5 trabajos excluidos de la tabla comparativa primaria (`\citet{ayinde2026ai, liabor2023cataloging, adetayo2023chatgpt, yan2023chatbotsslr, ayemowa2024genai}`) con el motivo (revisiones/artículos de posición, no sistemas primarios comparables), y §Panorama de trabajos relacionados los desarrolla como contexto. |
| 17 | Citar cada estudio incluido y presentar sus características | ✅ | Los 10 trabajos primarios están citados y caracterizados fila a fila en `tab:trabajos-comparativa`. |
| 18 | Presentar evaluaciones de riesgo de sesgo para cada estudio incluido | ❌ | Consecuencia directa del gap del ítem 11 -- no se evaluó riesgo de sesgo por estudio, por lo que no hay nada que presentar aquí. |
| 19 | Para todos los resultados, presentar por cada estudio: (a) estadísticas resumen por grupo y (b) una estimación de efecto con su precisión, idealmente en tablas o gráficos estructurados | ⚠️ | La tabla comparativa presenta datos cuantitativos puntuales cuando el resumen indexado los reportaba (p. ej. Kusuma et al.: $-25\,\%$ CPU, $-94\,\%$ tráfico de red), pero la mayoría de las celdas de "Evaluación empírica reportada" son cualitativas/descriptivas, sin una estimación de efecto ni intervalo de precisión sistemático para las 10 filas. |
| 20a | Para cada síntesis, resumir brevemente las características y el riesgo de sesgo entre los estudios que contribuyen | N/A | No hay una síntesis cuantitativa formal a la que aplicar este ítem (ver 13d). |
| 20b | Presentar los resultados de todas las síntesis estadísticas realizadas; si se hizo meta-análisis, presentar la estimación resumen con su precisión y medidas de heterogeneidad | N/A | No se realizó meta-análisis (ver 12, 13d). |
| 20c | Presentar los resultados de todas las investigaciones de posibles causas de heterogeneidad entre resultados | N/A | No aplica, mismo motivo que 13e. |
| 20d | Presentar los resultados de todos los análisis de sensibilidad realizados | N/A | No aplica, mismo motivo que 13f. |
| 21 | Presentar evaluaciones de riesgo de sesgo debido a resultados faltantes (sesgo de publicación) para cada síntesis evaluada | N/A | Consecuencia de 14 -- no se evaluó sesgo de publicación, por lo que no hay nada que presentar por síntesis. |
| 22 | Presentar evaluaciones de certeza (confianza) en el cuerpo de evidencia para cada resultado evaluado | N/A | Consecuencia de 15 -- no aplica el marco de certeza tipo GRADE a este tipo de revisión narrativa. |

## DISCUSIÓN

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 23a | Proveer una interpretación general de los resultados en el contexto de otra evidencia | ✅ | §Brecha identificada interpreta explícitamente los 10 trabajos primarios frente a las 4 características que combina SGB-SaaS, señalando cuál trabajo se acerca más en cada dimensión individual. |
| 23b | Discutir cualquier limitación de la evidencia incluida en la revisión | ✅ | Repetido de forma consistente en toda la tabla comparativa ("no confirmado a partir del resumen indexado disponible", acceso restringido por muro de pago editorial en varias filas) y explícito en la nota de honestidad metodológica antes de la tabla. |
| 23c | Discutir cualquier limitación de los procesos de revisión usados | ✅ | Dos advertencias explícitas de honestidad metodológica al cierre de §Brecha identificada: (1) la afirmación de brecha está acotada a las 30 referencias efectivamente revisadas, no a la totalidad de la literatura publicada; (2) la brecha se refiere a la combinación de las 4 características, no a la novedad individual de cada una. Retomado además como amenaza a la validez externa en el Cap. 12. |
| 23d | Discutir implicaciones de los resultados para la práctica, la política y la investigación futura | ⚠️ | El propio Cap. 3 no desarrolla explícitamente implicaciones de práctica/política más allá del propio proyecto; esa discusión más amplia se traslada al Cap. 9 (Discusión, §Comparación con trabajos relacionados) y al Cap. 10 (Trabajo Futuro), no se repite dentro del Cap. 3 mismo. |

## OTRA INFORMACIÓN

| # | Ítem oficial | Estado | Evidencia / nota |
|---|---|---|---|
| 24a | Proveer información de registro de la revisión (registro y número), o declarar que la revisión no fue registrada | ❌ | Esta revisión no fue pre-registrada (p. ej. en PROSPERO) -- correcto y esperable para un capítulo de PFC, no para una revisión sistemática clínica. Pero el propio Cap. 3 no incluye todavía una frase explícita del tipo "esta revisión no fue registrada formalmente" -- el ítem exige esa declaración explícita (en cualquiera de los dos sentidos) para considerarse cumplido, y hoy no existe. Se recomienda agregarla en una futura revisión del capítulo. |
| 24b | Indicar dónde puede accederse al protocolo de la revisión, o declarar que no se preparó un protocolo | ❌ | Mismo gap que 24a: no se preparó un protocolo formal pre-registrado, y esa ausencia tampoco está declarada explícitamente en el capítulo todavía. |
| 24c | Describir y explicar cualquier enmienda a la información provista en el registro o el protocolo | N/A | No aplica -- no existe protocolo ni registro que enmendar (consecuencia de 24a/24b). |
| 25 | Describir las fuentes de apoyo financiero o no financiero para la revisión, y el rol de los financiadores/patrocinadores | ✅ | `docs/capitulos/13-declaraciones.tex`, §13.7 (Financiamiento): declaración confirmada de que el proyecto no contó con financiamiento externo de ningún tipo -- aplica también a este capítulo específico, al no haber una fuente de financiamiento distinta para la revisión de literatura. |
| 26 | Declarar cualquier conflicto de interés de los autores de la revisión | ✅ | `docs/capitulos/13-declaraciones.tex`, §13.6 (Conflictos de interés): declaración confirmada de ausencia de conflictos de interés, aplicable a los mismos 3 autores que construyeron este capítulo. |
| 27 | Reportar cuáles de los siguientes están públicamente disponibles y dónde encontrarlos: formularios de recolección de datos, datos extraídos de los estudios incluidos, datos usados en todos los análisis, código analítico, cualquier otro material usado en la revisión | ⚠️ | `docs/bibliografia.bib` (las 34 referencias, incluida su verificación contra Crossref documentada en los comentarios del propio archivo) está versionado y disponible; la tabla comparativa en sí está en el código fuente LaTeX del capítulo (también versionado). No existe, en cambio, un formulario de extracción de datos separado y reutilizable (p. ej. una hoja de cálculo o CSV con las 8 variables por cada uno de los 10 estudios, independiente de la prosa/tabla del capítulo) -- disponibilidad parcial, no un paquete de datos de extracción dedicado. |

## Resumen de cumplimiento

| Sección | ✅ Cumple | ⚠️ Parcial | ❌ No cumple | N/A |
|---|---|---|---|---|
| Título (1) | 0 | 0 | 0 | 1 |
| Resumen (1) | 0 | 0 | 0 | 1 |
| Introducción (2) | 2 | 0 | 0 | 0 |
| Métodos (17 filas, incluidos subítems) | 4 | 4 | 4 | 5 |
| Resultados (11 filas, incluidos subítems) | 3 | 1 | 1 | 6 |
| Discusión (4) | 3 | 1 | 0 | 0 |
| Otra información (6) | 2 | 1 | 2 | 1 |
| **Total (42 filas)\*** | **14** | **7** | **7** | **14** |

\* *Nota de conteo: la tabla oficial de PRISMA 2020 tiene 27 ítems
numerados que se expanden a 42 filas por sus subítems (p. ej. 13a-13f,
20a-20d) -- el conteo de esta tabla coincide exactamente con esas 42
filas del documento oficial, una por cada ítem/subítem verificado
arriba.*

**Lectura honesta del resultado.** El Cap. 3 cumple bien la parte de la
revisión que sí se propuso ejecutar (elegibilidad, proceso de selección
con diagrama de flujo real, tabla comparativa, discusión de limitaciones
propias y de la literatura). La gran mayoría de los ítems marcados N/A
corresponden, de forma consistente, a exigencias propias de una revisión
sistemática con síntesis cuantitativa/meta-análisis -- exactamente lo que
el propio Cap. 3 declaró desde su primer párrafo que no iba a ejecutar.
Los ítems marcados ❌ (número de revisores/independencia del cribado,
evaluación formal de riesgo de sesgo por estudio, sesgo de publicación,
declaración explícita de no-registro/no-protocolo) son gaps reales y
honestos, no forzados a "no aplica" para inflar el resultado -- son
mejoras concretas y de bajo costo (en su mayoría, una frase declarativa
faltante) que podrían incorporarse en una futura revisión del capítulo.
