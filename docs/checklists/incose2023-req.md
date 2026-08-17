# Checklist INCOSE v4 — Características de requisitos individuales (C1-C9) y de conjunto (C10-C15)

**Proyecto**: SGB-SaaS — Entrega Final (Bloque A.3.1, D0R de la guía).
**Fuente evaluada**: [`docs/requisitos/SRS-v1.0.0.md`](../requisitos/SRS-v1.0.0.md), los 43 requisitos (28 `REQ-F`, 15 `REQ-NF`).
**Fecha**: 2026-08-12. **Commit base**: `e1f0c25` (incluye el fix de tests de
`REQ-F-001`/`REQ-NF-013` de la tarea inmediatamente anterior a este documento).
**Fuente cruzada**: `docs/trazabilidad/matriz.csv` (estados `implementado`/`verificado`) y el código real del repositorio, no solo el texto del SRS.

## Método y criterio de honestidad

Este checklist se construyó leyendo el texto completo de los 43 requisitos y
evaluando cada uno contra las 9 características INCOSE individuales (C1-C9),
más 6 características de conjunto (C10-C15) sobre el total. **No se marcó
todo como cumplido por defecto**: donde el texto real de un requisito tiene
una ambigüedad, una mezcla de varias reglas de negocio, un criterio
demasiado escueto, o una afirmación que ya no coincide con el estado real
del repositorio (algunas descubiertas durante esta misma revisión, no solo
las que el SRS ya declaraba), se marca ⚠️ con una nota numerada. Un
requisito puede tener más de una excepción.

**Leyenda**: ✅ cumple · ⚠️(n) no cumple, ver nota n · ⚠️(S) ver nota
sistémica **[S]**, aplicada a los 43 requisitos en la columna C9 (explicada
antes de la tabla, no repetida en cada fila).

### Nota sistémica de C9 — [S]

Ninguno de los 43 requisitos está redactado como una única cláusula
"[condición] [sujeto] shall [acción] [objeto] [restricción]" del patrón
formal de ISO/IEC/IEEE 29148:2018. El formato real de este SRS (heredado de
la convención ya usada en `matriz.csv`/HU/CU del repositorio, sección 3.0)
separa **Descripción** (sujeto + modal + acción + objeto, en prosa) de
**Criterio de aceptación medible** (las condiciones/restricciones, listadas
aparte en bullets) — más cercano a un híbrido Volere/Cockburn/Gherkin que al
template EARS de una sola oración. Además, el modal usado varía de forma
real entre requisitos: la mayoría usa "debe" (obligación, equivalente
informal a *shall*), pero varios usan "puede"/"pueden" (`REQ-F-015`,
`REQ-F-017`, `REQ-F-018`, `REQ-F-019`, `REQ-F-021`, `REQ-F-023`,
`REQ-F-024`, `REQ-F-028` — permiso/capacidad, más cercano a *may* que a
*shall*), "deberían" condicional (`REQ-NF-012`, `REQ-NF-014`, consistente
con su prioridad Should pero formalmente más débil que *shall*), o ninguna
partícula modal, en oraciones declarativas de presente (`REQ-F-008`,
`REQ-F-010`, `REQ-F-020`, `REQ-F-022`, `REQ-F-025`, `REQ-F-026`,
`REQ-F-027`, `REQ-NF-008`, `REQ-NF-009`). Esta es una desviación
**estructural y sistemática** del patrón formal, no un defecto puntual de
alguno de los 43 — se documenta una sola vez aquí en vez de fabricar una
justificación distinta por fila. No implica que el contenido sea incorrecto
o inservible: el formato Descripción+Rationale+Criterio es, de hecho, más
legible para un evaluador humano que una cadena EARS densa — pero, por la
letra estricta de C9, ninguno de los 43 conforma al patrón pedido.

## Tabla C1-C9

| ID | C1 | C2 | C3 | C4 | C5 | C6 | C7 | C8 | C9 |
|---|---|---|---|---|---|---|---|---|---|
| REQ-F-001 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(7,8) | ⚠️(S) |
| REQ-F-002 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-003 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-004 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-005 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-006 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-007 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-008 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-009 | ✅ | ✅ | ⚠️(3) | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-010 | ⚠️(1) | ⚠️(2) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-011 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-012 | ✅ | ⚠️(2) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-013 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-014 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-015 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-016 | ✅ | ✅ | ⚠️(3) | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-017 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-018 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-019 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-020 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-021 | ✅ | ⚠️(2) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-022 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-023 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-024 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-025 | ⚠️(1) | ⚠️(2) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-026 | ⚠️(1) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-027 | ⚠️(1) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-F-028 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-001 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-002 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-003 | ✅ | ✅ | ⚠️(10) | ✅ | ✅ | ✅ | ✅ | ⚠️(10) | ⚠️(S) |
| REQ-NF-004 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-005 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-006 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-007 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-008 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-009 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-010 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(9) | ⚠️(S) |
| REQ-NF-011 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-012 | ✅ | ✅ | ✅ | ⚠️(4) | ⚠️(5) | ✅ | ⚠️(6) | ✅ | ⚠️(S) |
| REQ-NF-013 | ✅ | ⚠️(2) | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️(8) | ⚠️(S) |
| REQ-NF-014 | ✅ | ✅ | ✅ | ⚠️(4) | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |
| REQ-NF-015 | ✅ | ✅ | ✅ | ✅ | ⚠️(5) | ✅ | ✅ | ✅ | ⚠️(S) |

### Notas al pie

1. **C1 (Necessary)** — `REQ-F-010`, `REQ-F-025`, `REQ-F-026`, `REQ-F-027`:
   el propio SRS declara "sin HU/CU dedicada... se infiere un uso gerencial,
   sin fabricar un rationale más elaborado del que el repositorio realmente
   sostiene". Sin un documento de negocio que respalde la necesidad, la
   característica "el sistema realmente lo necesita" queda apoyada solo en
   la existencia del código, no en una demanda documentada — mismo criterio
   de honestidad que el propio SRS ya aplica, extendido aquí a C1.
2. **C2 (Appropriate)** — `REQ-F-010`, `REQ-F-012`, `REQ-F-021`,
   `REQ-F-025`, `REQ-NF-013`: el criterio de aceptación medible tiene un
   solo comportamiento verificable (o ninguno, en el caso de `REQ-NF-013`,
   que es puramente cualitativo — "ninguna consulta concatena SQL"), sin
   códigos HTTP explícitos ni casos borde, notablemente más escueto que
   requisitos hermanos del mismo módulo (p. ej. `REQ-F-013`/`REQ-F-019`
   enumeran 2-3 escenarios con código de respuesta cada uno).
3. **C3 (Unambiguous)** — `REQ-F-009` (criterio 3: "mensaje explícito, no
   tabla vacía sin contexto") y `REQ-F-016` (criterio 4: "mensaje de error
   sin cerrar el formulario"): son criterios de UX subjetivos — no definen
   qué cuenta objetivamente como "explícito" o como un "mensaje de error"
   válido, dejando margen de interpretación a quien implemente o verifique.
4. **C4 (Complete)** — `REQ-NF-012`, `REQ-NF-014`: el propio requisito
   declara que su criterio de aceptación es "para cuando se implemente TLS
   real" (`REQ-NF-012`) o que la mitad del criterio (CSP en
   `nginx.conf`) está "pendiente" (`REQ-NF-014`) — el requisito, tal como
   está redactado hoy, no contiene toda la información necesaria para
   considerarse cerrado, por diseño (es una decisión ya tomada de dejar la
   parte faltante fuera de esta entrega, no un olvido).
5. **C5 (Singular)** — agrupan más de una acción o regla de negocio
   distinguible bajo un solo id: `REQ-F-005` (listar catálogo + ver
   detalle), `REQ-F-006` (crear + editar + dar de baja), `REQ-F-016` (crear
   préstamo + registrar devolución, dos operaciones de UI), `REQ-F-017`
   (listar + editar configuración), `REQ-F-019` (consulta de QR propio +
   registro de préstamo vía QR — el propio título del requisito nombra dos
   operaciones), `REQ-F-022` (tres mecanismos de alerta independientes:
   vencimiento/multa/reserva caducada, con triggers y componentes
   distintos), `REQ-F-023` (listar padrón + cambiar rol/estado, dos
   permisos distintos), `REQ-NF-004` (regla CRUD-vía-JPA + regla
   multi-tabla-vía-SP), `REQ-NF-007` (tres tipos de evento:
   LOGIN_OK/LOGIN_FAIL/LOGOUT), `REQ-NF-012` (decisión de arquitectura +
   preparación del backend + activación real de TLS, tres sub-partes con
   estados de cierre distintos bajo un solo id), `REQ-NF-014` (CSP +
   supresión de stacktraces + deshabilitar Swagger + usuario no-root: 4
   controles de seguridad distintos), `REQ-NF-015` (pipeline de CI +
   automatización de `Makefile` + documentación OpenAPI, tres entregables
   distintos).
6. **C7 (Verifiable)** — `REQ-NF-012`: el propio requisito declara
   textualmente que "TLS real activo end-to-end sigue sin Test ni
   Demonstration — no hay stack con certificado real contra el cual
   verificar". No existe, a la fecha, ningún medio objetivo de comprobación
   para la parte del requisito que sigue sin implementarse.
7. **C8 (Correct)** — `REQ-F-001`: describe el estado inicial tras el
   registro como `ACTIVO`, pero `REQ-F-020` (y la sección 6, ítem 13, del
   propio SRS) documenta que el estado real tras el registro es
   `PENDIENTE_VERIFICACION` desde que se cerró el flujo de verificación de
   correo. La contradicción ya está autodeclarada por el propio documento
   — no es un hallazgo nuevo de este checklist, pero sí una violación real
   de C8 mientras `REQ-F-001` no se reescriba.
8. **C8 (Correct)** — `REQ-F-001` y `REQ-NF-013`: ambos textos afirman que
   ciertos criterios de aceptación "no tienen prueba automatizada de
   regresión" / están "sin test de regresión permanente en el suite". Esto
   ya **no es cierto**: el commit `e1f0c25` (tarea inmediatamente anterior
   a este checklist, misma sesión) agregó
   `AuthControllerTest.registro_passwordCorta_devuelve400ProblemDetail` y
   `AuthServiceTest.registroConPayloadDeInyeccionSql_seGuardaComoTextoLiteralSinLanzarExcepcion`,
   y `docs/trazabilidad/matriz.csv` ya refleja el estado `verificado` para
   ambos requisitos — pero el texto en prosa de `SRS-v1.0.0.md` no se
   actualizó junto con la matriz. Hallazgo nuevo de esta revisión, no
   señalado previamente en la sección 6 del SRS.
9. **C8 (Correct)** — `REQ-NF-010`: declara que la verificación de rol
   aplica a "cada endpoint", pero el propio requisito reconoce en su nota
   de honestidad una asimetría real: `LibroController` incluye `ADMIN` en
   sus 5 endpoints, `PrestamoController`/`ReservacionController` no. El
   cuantificador "cada" del enunciado principal no es literalmente cierto
   para el 100% de los controllers.
10. **C3 y C8** — `REQ-NF-003`: cita dos mediciones de la misma condición
    (latencia en cache frío) con valores contradictorios sin reconciliar:
    "~170ms" según `docs/arquitectura/ISO25010.md` frente a "7.96ms" según
    la corrida k6 de `docs/mediciones/perf/REPORT.md` — más de 20x de
    diferencia para, en teoría, el mismo escenario. El valor de cache
    caliente sí coincide entre ambas fuentes (~30ms vs 29.79ms), lo que
    hace más notoria la discrepancia del otro valor. Ninguna de las dos
    citas indica por qué difieren (¿condiciones de carga distintas?,
    ¿definición distinta de "cache frío"?) — ambigüedad real (C3) que
    también compromete cuál de las dos cifras describe correctamente el
    sistema (C8). Hallazgo nuevo de esta revisión.

## Características del conjunto (C10-C15)

**C10 — Complete.** El conjunto de 43 requisitos cubre exactamente el
alcance declarado en la sección 1.2 del SRS: los 9 módulos funcionales
listados ahí (Auth, Libros/Catálogo, Préstamos, Reservaciones, Multas,
Credencial QR, Notificaciones, Administración/auditoría, Configuración,
Chatbot) tienen al menos un requisito, y no hay módulo del código real
(`backend-springboot/src/main/java/com/uteq/backend/controller/`, 15
`@RestController`) sin representación en algún `REQ-F`. Lo que queda fuera
(integración académica externa, TLS activo end-to-end, Google Books API,
evidencia SUS) está **excluido explícitamente** en esa misma sección, no
omitido en silencio — eso es evidencia a favor de C10, no en contra: un
conjunto "completo" respecto a un alcance mal definido sería sospechoso;
este declara su borde y se mantiene dentro de él. **Veredicto: ✅**,
relativo al alcance que el propio documento se fija (no una afirmación de
que el producto cubre todo lo que una biblioteca real necesitaría).

**C11 — Consistent.** Se identificaron **dos** inconsistencias reales
dentro del conjunto, no ninguna: (1) el estado inicial de cuenta tras el
registro, `ACTIVO` según `REQ-F-001` vs. `PENDIENTE_VERIFICACION` según
`REQ-F-020` — ya autodeclarada por el SRS (sección 6, ítem 13; ver nota 7
arriba); (2) los valores contradictorios de latencia en cache frío entre
`REQ-NF-003` y las dos fuentes que cita (ver nota 10), no señalada
previamente. Ninguna de las dos es una contradicción de *comportamiento del
sistema* (el código hace una sola cosa en cada caso; lo que diverge es la
documentación describiéndolo), pero ambas son, por definición, violaciones
de C11 tal como está escrito el conjunto hoy. **Veredicto: ⚠️** — no se
declara consistencia perfecta donde no la hay.

**C12 — Feasible (conjunto, con los recursos del equipo).** Evidencia
directa y fuerte: de los 43 requisitos, **43/43** tienen `estado`
`implementado` o `verificado` en `docs/trazabilidad/matriz.csv` (ninguno en
un hipotético estado "pendiente" a nivel de todo el requisito — los gaps
reales, como TLS real o CSP de frontend, son sub-partes de requisitos
`Should` ya parcialmente cerrados, no requisitos enteros sin construir). Un
equipo de 3 personas sin dedicación exclusiva (restricción declarada en la
sección 2.4) ya implementó el conjunto completo dentro del plazo del PFC —
la factibilidad no es una proyección, es un hecho ya ocurrido.
**Veredicto: ✅**.

**C13 — Comprehensible.** A favor: estructura idéntica en los 43
requisitos (Prioridad/Fuente/Módulo/Descripción/Rationale/Criterio/Método),
glosario de acrónimos en 1.3, y las notas de honestidad in-line evitan que
un tercero infiera una cobertura mayor a la real. En contra (matizado, no
descalificante): la nota sistémica de C9 aplica aquí también — un
evaluador externo entrenado específicamente en el template EARS/shall de
INCOSE podría encontrar el formato narrativo menos inmediato de escanear
que una cadena "[condición] [sujeto] shall...", aunque sea igual de preciso
en contenido. **Veredicto: ✅**, con la salvedad de formato ya explicada.

**C14 — Able to be validated.** Evidencia directa: `matriz.csv` conecta
cada uno de los 43 requisitos a `modulo_codigo`, `endpoint_api`,
`prueba_automatizada` y `evidencia_empirica`, y
`scripts/validate-traceability.sh` corre en cada ejecución de CI
(`ci(trazabilidad): agrega scripts/validate-traceability.sh y lo integra a
CI`, commit `6c351cf`) validando que esa estructura no se rompa. La
mayoría de los requisitos `Must` tiene test automatizado real (ejecutado en
este mismo commit: `BUILD SUCCESS`, 203 tests) y/o evidencia de
`Demonstration` versionada en `docs/mediciones/`. **Veredicto: ✅**.

**C15 — Correct (conjunto).** El documento tiene un historial real de
autocorrección (cifra de ADRs 10→13, endpoints 19→44, reapertura del
estado de `REQ-NF-012`/`REQ-NF-014`), lo que demuestra intención activa de
mantener el conjunto correcto — pero esta misma revisión encontró **tres
casos concretos donde el conjunto ya no describe con precisión el estado
real** del repositorio: la desactualización de `REQ-F-001`/`REQ-NF-013`
tras el commit `e1f0c25` (nota 8), la asimetría de `REQ-NF-010` (nota 9,
autodeclarada pero sigue sin resolverse) y la contradicción numérica de
`REQ-NF-003` (nota 10, nueva). Para un equipo de 3 personas sin dedicación
exclusiva, mantener 43 requisitos en sincronía perfecta con cada commit del
código es un riesgo de mantenimiento real, no solo teórico — ya
materializado dos veces en esta sesión. **Veredicto: ⚠️** — correcto en su
mayoría, con drift real y concreto identificado, no una duda especulativa.

## Resumen

- **Requisitos evaluados**: 43 (28 `REQ-F`, 15 `REQ-NF`).
- **Considerando las 9 características (C1-C9), incluyendo la desviación
  sistémica de sintaxis C9**: **0 / 43 (0%)** pasan las 9 sin ninguna
  excepción; **43 / 43 (100%)** tienen al menos una excepción documentada
  — la totalidad de esa cifra está explicada por la nota sistémica **[S]**
  de C9 (ningún requisito de este proyecto se redactó nunca en sintaxis
  formal *shall*, no es un defecto puntual de contenido).
- **Considerando solo las 8 características de contenido (C1-C8),
  excluyendo la desviación sistémica de formato C9**: **20 / 43 (46.5%)**
  pasan las 8 sin ninguna excepción de contenido; **23 / 43 (53.5%)**
  tienen al menos una excepción de contenido documentada (típicamente C5
  — requisito compuesto — o C2 — criterio demasiado escueto; las de C8 son
  las menos frecuentes pero las más específicas: 4 requisitos con una
  afirmación que ya no coincide con el estado real verificado).
- Ninguna de las 43×9 celdas se marcó ✅ por defecto sin evaluarla: cada
  ⚠️ cita evidencia concreta (línea del propio SRS, contradicción con otro
  requisito, o estado real del repositorio verificado en este commit), y
  ninguna celda C6 (Feasible) tiene excepción — no se encontró ningún
  requisito técnicamente inviable con la arquitectura actual, a diferencia
  de C1/C2/C3/C4/C5/C7/C8/C9, donde sí se encontraron casos reales.
