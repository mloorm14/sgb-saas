# Checklist — Ralph et al. (2021), estándar empírico "Engineering Research"

**Proyecto**: SGB-SaaS — Entrega Final (Bloque E de la guía).
**Estándar evaluado**: Ralph, P. et al. (2021). *Empirical Standards for
Software Engineering Research*. arXiv:2010.03525 [cs.SE]
(`ralph2021empiricalstandards` en `docs/bibliografia.bib`), estándar
**"Engineering Research"** específicamente — ver justificación de tipo de
estudio abajo.
**Fecha**: 2026-08-13. **Commit base**: `6696bf1`.
**Fuente del estándar**: verificado línea por línea contra el documento
oficial `docs/standards/EngineeringResearch.md` del repositorio
[`acmsigsoft/EmpiricalStandards`](https://github.com/acmsigsoft/EmpiricalStandards)
(descargado directamente para esta tarea, no transcrito de memoria ni
aproximado).

## Por qué "Engineering Research" y no otro estándar

El repositorio `EmpiricalStandards` define 19 estándares distintos (uno
por tipo de estudio: `ActionResearch`, `CaseStudy`, `Experiments`,
`QuestionnaireSurveys`, `SystematicReviews`, etc.). SGB-SaaS no encaja en
ninguno de esos moldes clásicos: no hubo intervención del equipo
investigador en una organización real ya existente (descarta *Action
Research*), no se observó el uso del sistema por una organización real
independiente del propio equipo (descarta *Case Study* puro), y no hubo
participantes humanos asignados a condiciones experimentales controladas
(descarta *Experiments*, al menos para la evaluación técnica -- las
pruebas SUS pendientes, cuando se ejecuten, sí tendrían ese carácter, pero
a escala de un solo instrumento, no del proyecto completo). El propio
estándar `EngineeringResearch.md` define su alcance como *"research that
invents and evaluates technological artifacts, including [...] systems,
tools, and other computer-based technologies"* -- exactamente lo que
SGB-SaaS es: un artefacto de software construido por el equipo y evaluado
empíricamente contra sus propios requisitos y contra el estado del arte
(\autoref{cap:trabajos-relacionados} del informe). Es, por esto, el
estándar que mejor describe el tipo de estudio de este PFC.

## Método y criterio de honestidad

Cada ítem se evalúa contra evidencia real y verificable del repositorio:
un capítulo del informe (`docs/informe-final.pdf`), un archivo de
mediciones (`docs/mediciones/`), o código/configuración fuente. **No se
marca un ítem como cumplido sin poder señalar el archivo o la sección
exacta que lo sustenta.** Donde el estándar no aplica al tipo de artefacto
de este proyecto (p. ej. pruebas de corrección formal, porque SGB-SaaS no
tiene contribuciones analíticas/teóricas de tipo teorema o algoritmo), se
marca explícitamente **N/A** con su justificación, en vez de omitirse en
silencio o forzarse como cumplido.

**Leyenda**: ✅ cumple · ⚠️ cumple parcialmente (ver nota) · ❌ no cumple ·
**N/A** no aplica a este tipo de artefacto (ver justificación)

## Atributos esenciales (`Essential Attributes`)

| # | Ítem (traducción fiel del estándar) | Estado | Evidencia / nota |
|---|---|---|---|
| E1 | Describe el artefacto propuesto con detalle adecuado | ✅ | Modelo C4 completo en Structurizr DSL (`docs/arquitectura/workspace.dsl`), 13 Architecture Decision Records, y los Capítulos 6 (Diseño y Arquitectura) y 7 (Implementación) del informe describen el flujo completo, los componentes y las decisiones técnicas del sistema. |
| E2 | Justifica la necesidad, utilidad o relevancia del artefacto propuesto | ✅ | Cap. 1 (Introducción), §Contexto y motivación: fricción operativa real y verificable del dominio bibliotecario (registro manual, ausencia de autoservicio). Nota de honestidad ya declarada ahí mismo: el documento evita deliberadamente citar una cifra de "población afectada" sin fuente verificada -- la justificación es cualitativa y de dominio, no una cifra inventada. |
| E3 | Evalúa conceptualmente el artefacto: discute fortalezas, debilidades y limitaciones | ✅ | Cap. 12 (Amenazas a la Validez) y Cap. 9 (Discusión) discuten debilidades reales (p. ej. la comparación `cache_frío`/`cache_caliente` no aísla limpiamente el efecto del caché); los 13 ADR documentan explícitamente los *trade-offs* de cada decisión arquitectónica, no solo la decisión final. |
| E4 | Evalúa empíricamente el artefacto usando: *action research*, *case study*, experimento controlado, simulación cuantitativa, *benchmarking study*, u otro método con justificación clara | ⚠️ | El Cap. 8 (Resultados) reporta evaluación empírica real (k6, JaCoCo, OWASP, Lighthouse), pero ninguno de sus 5 bloques se declara explícitamente como uno de los 5 métodos canónicos del estándar. El bloque de rendimiento (k6, 5 corridas pareadas `cache_frío` vs.\ `cache_caliente`) se acerca más a un *benchmarking study* (el artefacto se evalúa contra sí mismo bajo dos condiciones), pero el Cap. 5 (Materiales y Métodos) enmarca la metodología con GQM y DSR, no con la taxonomía de Ralph et al. -- no hay una frase explícita del tipo "este es un *benchmarking study* según el estándar X". |
| E5 | Indica claramente cuál de esas metodologías empíricas se usó | ❌ | Consecuencia directa de E4: no existe, en ningún capítulo del informe, una declaración explícita que etiquete la evaluación empírica de SGB-SaaS con la taxonomía de este estándar. Gap real, no solo de redacción -- se documenta aquí como ítem de trabajo futuro para una revisión posterior del Cap. 5. |
| E6 | O BIEN discute alternativas del estado del arte (con sus fortalezas/debilidades/limitaciones) O BIEN explica por qué no existen O BIEN argumenta de forma convincente que la comparación directa es impráctica | ✅ | Cap. 3 (Trabajos Relacionados) completo: 10 trabajos primarios comparados fila a fila (`tab:trabajos-comparativa`) con columnas explícitas de limitaciones declaradas y pila tecnológica, más una sección dedicada (§Brecha identificada) que discute exactamente esto. |
| E7 | O BIEN compara empíricamente el artefacto con una o más alternativas del estado del arte O BIEN lo compara con *benchmarks* establecidos O BIEN justifica por qué la evaluación comparativa es impráctica | ⚠️ | La comparación del Cap. 3 es **descriptiva/cualitativa** (dimensiones de arquitectura, evaluación reportada, limitaciones), no una comparación empírica directa (mismo protocolo de medición aplicado a SGB-SaaS y a un sistema alternativo real) -- ninguno de los 10 trabajos comparados tiene código o despliegue disponible para ejecutar el mismo protocolo k6/JaCoCo/Lighthouse. El documento no incluye, sin embargo, una frase explícita de tipo "la comparación empírica directa es impráctica porque ninguno de los sistemas comparables tiene código disponible" -- la razón es real y se infiere de la propia tabla comparativa, pero no está declarada como justificación formal en ningún lugar. |
| E8 | Los supuestos (si los hay) son explícitos, plausibles y no se contradicen entre sí ni con los objetivos de la contribución | ✅ | `docs/arquitectura/ISO25010.md` declara explícitamente el supuesto de carga (biblioteca universitaria: volumen bajo, ráfagas puntuales, no tráfico constante de alta concurrencia), usado consistentemente para justificar la prioridad *Media* (no *Alta*) de eficiencia de desempeño; el Cap. 5 (§GQM) declara los supuestos del protocolo de medición del caché Redis. |
| E9 | Usa notación de forma consistente (si se usa alguna notación) | N/A | SGB-SaaS no presenta notación matemática o algorítmica formal (no hay teoremas, pseudocódigo de algoritmos propios ni modelos formales) -- el artefacto es un sistema web, no una contribución analítica. El ítem no aplica por la propia naturaleza del artefacto, no porque se haya omitido una notación que sí debería existir. |

## Atributos deseables (`Desirable Attributes`)

| # | Ítem | Estado | Evidencia / nota |
|---|---|---|---|
| D1 | Provee materiales suplementarios: código fuente (si el artefacto es software) y conjuntos de datos de entrada (si aplica) | ✅ | Repositorio completo público bajo MIT (`docs/capitulos/13-declaraciones.tex`, §13.1); datos crudos de todas las mediciones empíricas versionados en `docs/mediciones/` (§13.2 del mismo capítulo). |
| D2 | Justifica, con motivos prácticos o éticos, cualquier elemento faltante del paquete de replicación | ✅ | Los formularios de consentimiento informado firmados de las pruebas SUS se excluyen explícitamente del repositorio por motivos éticos de protección de datos personales, declarado en `docs/etica/ETHICS.md` y en `docs/capitulos/13-declaraciones.tex` §13.4. |
| D3 | Discute la base teórica del artefacto | ✅ | Cap. 4 (Marco Teórico) completo: ISO/IEC/IEEE 29148:2018, modelo C4, ISO/IEC 25010, principios REST, JWT, OWASP Top 10, patrones ORM/SP/cache-aside. |
| D4 | Provee argumentos de corrección para las contribuciones analíticas y teóricas clave (p. ej. teoremas, análisis de complejidad, demostraciones matemáticas) | N/A | SGB-SaaS no tiene contribuciones analíticas o teóricas de este tipo (no propone un algoritmo nuevo con complejidad demostrable ni un modelo matemático propio) -- el ítem no aplica al tipo de artefacto. |
| D5 | Incluye uno o más ejemplos funcionando para ilustrar el artefacto | ✅ | `docs/postman/coleccion.json` (66 peticiones HTTP reales, casos de éxito y de error); listados de código real (`\lstlisting`) en el Cap. 7 (Implementación), incluyendo el flujo completo de invocación de un procedimiento almacenado multi-`OUT`. |
| D6 | Evalúa el artefacto en un contexto relevante para la industria (p. ej. proyectos de código abierto ampliamente usados, programadores profesionales) | ❌ | SGB-SaaS no tiene todavía un despliegue público ni usuarios reales evaluándolo -- toda la evaluación empírica del Cap. 8 se ejecutó en el entorno de desarrollo del propio equipo (`docker compose`), no en un contexto de uso real. Coincide con el gap ya declarado de $N=0$ en la evidencia SUS (\autoref{sec:res-usabilidad}) y con el despliegue público todavía pendiente (Cap. 10, Trabajo Futuro). |

## Atributos extraordinarios (`Extraordinary Attributes`)

Estos dos atributos son explícitamente aspiracionales según el propio
estándar (no exigidos ni siquiera para una publicación de alto nivel) --
se marcan como no reclamados, con honestidad, en vez de forzar una
afirmación grandilocuente sobre el alcance de un PFC académico:

| # | Ítem | Estado | Nota |
|---|---|---|---|
| X1 | Contribuye a la comprensión colectiva de prácticas o principios de diseño | N/A | No se reclama -- alcance de PFC académico, no de investigación original en principios de diseño de software. |
| X2 | Presenta innovaciones revolucionarias con beneficios obvios en el mundo real | N/A | No se reclama, por el mismo motivo. |

## Auto-verificación de antipatrones (`Antipatterns`)

El estándar lista 6 antipatrones a evitar. Esta sección es un
autoexamen honesto, no parte del conteo de cumplimiento de arriba:

| Antipatrón | ¿Se evitó? | Nota |
|---|---|---|
| Sobreestimar la novedad de la contribución | ✅ Evitado | Cap. 3, §Brecha identificada, declara dos advertencias explícitas de honestidad metodológica que acotan la afirmación de brecha (alcance limitado a las 30 referencias revisadas, brecha referida a la *combinación* de características, no a cada una por separado). |
| Omitir detalles conceptuales clave centrándose solo en aspectos incidentales de implementación | ✅ Evitado | Cap. 4 (Marco Teórico) y Cap. 6 (Diseño) cubren extensamente el "por qué" antes que el "cómo". |
| La evaluación consiste únicamente en recoger opiniones de usuarios | ✅ Evitado | $N=0$ en SUS -- no hay ninguna opinión de usuario recogida todavía; el resto de la evaluación (k6, JaCoCo, OWASP, Lighthouse) es técnica, no de opinión. |
| La evaluación consiste únicamente en datos de rendimiento cuantitativos no comparados contra *benchmarks* o alternativas establecidas | ⚠️ Riesgo parcial presente | El bloque de rendimiento (k6) compara dos escenarios internos del propio SGB-SaaS (`cache_frío` vs.\ `cache_caliente`), no contra un sistema alternativo externo ni contra un *benchmark* de la industria -- coincide con el gap ya identificado en E7. |
| Diseño no experimental (un solo grupo, sin repetición) | ⚠️ Riesgo parcial presente | El bloque de rendimiento sí es repetido (5 corridas independientes, prueba de Wilcoxon pareada) -- evitado ahí. El bloque de Lighthouse, con solo $n=2$ corridas (ambas de perfil móvil, ninguna de escritorio, ya declarado como limitación en el Cap. 8), es más cercano al patrón de antipatrón que se advierte evitar. |
| Evaluación usando ejemplos de juguete (a veces presentados como "estudios de caso") | ✅ Evitado | Los datos de ejemplo del sistema son libros reales publicados con ISBN real (`db/seed.sql`, verificado en `docs/etica/ETHICS.md`), y los escenarios de carga de k6 reflejan el perfil de uso real declarado de una biblioteca universitaria, no datos ni escenarios artificiales sin relación con el dominio. |

## Resumen de cumplimiento

| Categoría | ✅ Cumple | ⚠️ Parcial | ❌ No cumple | N/A |
|---|---|---|---|---|
| Esenciales (9 ítems) | 5 | 2 | 1 | 1 |
| Deseables (6 ítems) | 4 | 0 | 1 | 1 |
| Extraordinarios (2 ítems) | 0 | 0 | 0 | 2 |
| **Total (17 ítems)** | **9** | **2** | **2** | **4** |

**Lectura honesta del resultado.** SGB-SaaS cumple sólidamente la
descripción del artefacto, su justificación, su evaluación conceptual, la
discusión de alternativas del estado del arte y la mayoría de los
atributos deseables. Los dos gaps de "no cumple" más relevantes son
reales y ya están, de hecho, conectados con limitaciones declaradas en
otras partes del informe: (E5/D6) la falta de una etiqueta explícita de
metodología empírica según esta taxonomía, y la ausencia de un contexto
de evaluación industrial real (sin despliegue público todavía, sin
usuarios reales, $N=0$ en SUS). Ninguno de los dos se fuerza como
cumplido ni se omite.
