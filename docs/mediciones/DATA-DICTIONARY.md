# Diccionario de datos — `docs/mediciones/`

Documenta los campos/variables que aparecen en la evidencia cruda ya
existente en `docs/mediciones/`, requisito E.3 de la guía de la Tercera
Entrega.

> **Alcance actual**: además de los 2 archivos originales de
> `docs/mediciones/sec/` (julio de 2026), este diccionario documenta
> las 5 corridas de k6 (`docs/mediciones/perf/`), las 16 evidencias
> OWASP (`docs/mediciones/sec/`), los 3 reportes JaCoCo
> (`docs/mediciones/jacoco/`), las 2 corridas de Lighthouse
> (`docs/mediciones/lighthouse/`) y la estructura del archivo SUS
> (`docs/mediciones/sus/sus.csv`, datos mock — el bloque de usabilidad
> sigue en $N=0$, ver `OBS-08`).

## `docs/mediciones/sec/2026-07-21-cookie-refresh-token.md`

Evidencia de la migración del `refreshToken` a cookie HttpOnly (ver
`docs/adr/adr-012-cookies-jwt.md`).

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `Fecha (ISO 8601 UTC)` | timestamp | — | fecha/hora válida en formato `YYYY-MM-DDTHH:MM:SSZ` | Momento real en que se ejecutó la verificación, tomado del header `Date` de una respuesta HTTP real, no del reloj local sin verificar. |
| `Commit` | string (hash corto de git) | — | 7 caracteres hexadecimales | Commit exacto contra el que se corrió la verificación, para poder reproducirla exactamente sobre ese estado del código. |
| `Set-Cookie: refreshToken` | string (JWT compacto, 3 partes separadas por `.`) | — | JWT firmado HS256 válido | Token de refresco emitido por `/api/auth/login` o `/api/auth/refresh`, transportado como valor de la cookie. |
| `Path` (atributo de cookie) | string | — | siempre `/api/auth` en este proyecto | Restringe el envío automático de la cookie a las rutas de autenticación; el navegador no la adjunta en llamadas a `/api/v1/**`. |
| `Max-Age` (atributo de cookie) | entero | segundos | `604800` (7 días) en login/refresh; `0` en logout | Vida de la cookie; debe coincidir con `security.jwt.refresh-expiration-ms` del backend. `0` fuerza expiración inmediata (logout). |
| `Secure`, `HttpOnly`, `SameSite=Strict` (atributos de cookie) | booleano (presencia/ausencia) | — | los 3 presentes siempre | Los 3 atributos de seguridad exigidos por el requisito A.1; su ausencia sería un hallazgo de seguridad, no un valor "fuera de rango" tolerable. |
| `HTTPSTATUS` / código de estado HTTP | entero | — | `200` (login/refresh exitoso), `400` (refresh sin cookie), `204` (logout) | Código de respuesta observado para cada escenario probado (positivo y negativo). |
| `BODY_BYTES` | entero | bytes | `0` para `204 No Content`; > 0 para respuestas con cuerpo JSON | Tamaño del cuerpo de la respuesta; se usa para confirmar ausencia de fuga de datos (ej. que el `refreshToken` no aparece en el cuerpo de `/login`). |

## `docs/mediciones/sec/2026-07-21-cache-libros-ttl.md`

Evidencia del TTL externo del cache Redis `"libros"` (ver
`docs/adr/adr-008-ttl-cache-libros.md`).

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `Fecha (ISO 8601 UTC)` | timestamp | — | fecha/hora válida | Igual que en la evidencia de cookies — momento real de la corrida. |
| `Commit` | string (hash corto de git) | — | 7 caracteres hexadecimales | Commit exacto verificado. |
| `TTL` (salida de `redis-cli TTL <key>`) | entero | segundos | `-1` (sin expiración, estado previo al fix); `0`–`300` (tras el fix, cuenta regresiva desde el default `CACHE_LIBROS_TTL_SECONDS`) | Segundos restantes antes de que Redis expire la entrada de cache automáticamente. `-1` es el valor especial de Redis para "existe pero sin TTL configurado". |
| Nombre de key de Redis (ej. `libros::Page request [...]` / `libros::SimpleKey []`) | string | — | prefijo `libros::` siempre | Key generada por Spring Cache a partir del nombre del cache (`"libros"`) y los argumentos del método `listar(Pageable)`; varía según los parámetros de paginación/orden de cada llamada. |
| `TIME` (tiempo de respuesta de `curl -w %{time_total}`) | decimal | segundos | ~0.15–0.20s en cache miss; ~0.01–0.03s en cache hit | Latencia observada de `GET /api/v1/libros`; la diferencia entre llamada 1 (miss) y llamada 2 (hit) es la evidencia de que el cache sirve la segunda lectura. |
| `BODY_BYTES` | entero | bytes | igual entre llamada 1 y 2 para la misma consulta | Confirma que el cuerpo de la respuesta cacheada es idéntico byte a byte al original, no una respuesta distinta o truncada. |
| `HTTPSTATUS` | entero | — | `200` en ambas llamadas | Código de respuesta esperado para una lectura exitosa del catálogo. |

## `docs/mediciones/perf/k6-run{1..5}.json`

Salida cruda de k6 en formato **NDJSON** (un objeto JSON por línea, sin
envoltorio de arreglo) para las 5 corridas del escenario de rendimiento
`cache_caliente` vs.\ `cache_frío` (`k6/libros-listado-test.js`). Campos
verificados abriendo `k6-run1.json` directamente para esta tarea, no
asumidos del formato estándar documentado de k6 -- la estructura real
tiene dos tipos de línea distintos:

**Líneas `"type":"Metric"`** (una por cada una de las 16 métricas que k6
declara al inicio de la corrida -- `checks`, `data_received`,
`data_sent`, `http_req_blocked`, `http_req_connecting`,
`http_req_duration`, `http_req_failed`, `http_req_receiving`,
`http_req_sending`, `http_req_tls_handshaking`, `http_req_waiting`,
`http_reqs`, `iteration_duration`, `iterations`, `vus`, `vus_max`):

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `type` | string | — | siempre `"Metric"` en estas líneas | Distingue una línea de declaración de métrica de una línea de dato (`"Point"`, ver tabla siguiente). |
| `data.name` | string | — | uno de los 16 nombres listados arriba | Nombre interno de la métrica que k6 está declarando. |
| `data.type` | string (enum) | — | `counter`, `trend`, `rate`, `gauge` | Tipo estadístico de la métrica: `counter` acumula (ej. `http_reqs`), `trend` guarda una distribución (ej. `http_req_duration`, la métrica clave para el `p95` del \autoref{cap:resultados}), `rate` es una proporción 0-1 (ej. `http_req_failed`), `gauge` es un valor instantáneo (ej. `vus`). |
| `data.thresholds` | arreglo de strings | — | vacío en estas corridas | Umbrales k6 configurados para la métrica (no se usaron umbrales de aborto automático en este proyecto; los umbrales de p95 se evalúan a posteriori con `scripts/perf-analysis.py`, no en tiempo real por k6). |
| `data.submetrics` | arreglo de objetos, o `null` | — | poblado solo en `http_req_duration` (submétricas por `scenario`) | Para `http_req_duration`, declara las submétricas etiquetadas `scenario:cache_caliente` y `scenario:cache_frio` que separan ambos escenarios del reporte agregado. |
| `metric` | string | — | igual a `data.name` | Nombre de la métrica, repetido a nivel raíz del objeto (redundante con `data.name`, así lo emite k6). |

**Líneas `"type":"Point"`** (una por cada muestra real registrada durante
la corrida -- la mayoría del archivo):

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `metric` | string | — | uno de los 16 nombres de métrica | A qué métrica pertenece esta muestra puntual. |
| `data.time` | timestamp ISO 8601 (UTC, con nanosegundos) | — | fecha/hora dentro de la ventana real de ejecución de la corrida | Momento exacto en que k6 registró la muestra. |
| `data.value` | decimal | depende de la métrica: ms (`http_req_duration` y demás `http_req_*`), bytes (`data_received`/`data_sent`), entero (`vus`, `iterations`), booleano 0/1 (`checks`, `http_req_failed`) | p. ej. `http_req_duration` observado en el rango aproximado 5-350ms en estas corridas | Valor numérico de la muestra; es el campo que agrega `scripts/perf-analysis.py` para calcular medianas, IC~95\,\% y percentiles (p90/p95/p99) del \autoref{tab:res-perf-descriptivo}. |
| `data.tags.scenario` | string | — | `cache_caliente` o `cache_frio` | Identifica a cuál de los dos escenarios comparados (\autoref{sec:res-rendimiento}) pertenece la muestra; ausente en las peticiones de `::setup` (login inicial, fuera de ambos escenarios). |
| `data.tags.method` | string | — | `GET`, `POST` | Verbo HTTP de la petición que generó la muestra. |
| `data.tags.name` | string (URL) | — | URL completa de la petición dentro de la red Docker (ej. `http://backend:8080/api/v1/libros?page=0&size=10`) | Identifica el endpoint exacto ejercitado. |
| `data.tags.status` | string (código HTTP como texto) | — | `200` en el camino esperado | Código de respuesta HTTP de esa petición específica. |
| `data.tags.expected_response` | string (`"true"`/`"false"`) | — | `"true"` en el camino esperado | Si k6 clasificó la respuesta como exitosa según su lógica interna de umbral por defecto (2xx/3xx). |
| `data.tags.group` | string | — | `""` (fuera de un `group()`) o `"::setup"` | Agrupación lógica del script k6; `::setup` marca las peticiones de la fase de login inicial, no del escenario medido. |

## `docs/mediciones/sec/*.md` (16 archivos, evidencia OWASP y de seguridad)

Los 16 archivos comparten la **misma estructura de cabecera**, definida
por la plantilla `docs/mediciones/TEMPLATE.md` y generada con
`scripts/mediciones-header.sh`. Se documenta aquí de forma generalizada
en vez de repetir la misma tabla 16 veces -- los campos son idénticos en
cada archivo, solo cambia el valor concreto:

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `Fecha (ISO 8601 UTC)` | timestamp | — | fecha/hora válida | Momento real de la corrida (rango observado en los 16 archivos: 2026-07-30 a 2026-08-11). |
| `Commit` | string (hash corto de git) | — | 7 caracteres hexadecimales | Commit exacto verificado; permite reproducir la evidencia sobre el mismo estado de código. |
| `Docker` / `Docker Compose` | string (versión) | — | versión de Docker/Compose reportada por `docker --version` / `docker compose version` | Entorno de ejecución del stack real (no un mock), constante en todos los archivos de esta tarea: Docker 29.5.3, Compose v5.1.4. |
| `Java` / `Maven` | string (versión) | — | versión reportada por `java -version` / `mvn -version` | Entorno del backend real. |
| `PostgreSQL` / `Redis` | string (versión) | — | versión del contenedor real | Entorno de datos real (no una base de datos en memoria). |

Sección `Metodología / comando ejecutado`: siempre incluye el/los
comando(s) `curl` exactos usados, contra el stack Docker real levantado
con `docker compose up -d --build`.

Sección `Resultados crudos` -- **dos patrones distintos verificados**, no
uno solo (corrección respecto al patrón asumido inicialmente para esta
tarea: `HTTPSTATUS`/`BODY_BYTES` con `curl -w` **no** es el patrón usado
en los 14 archivos de auditoría OWASP; es específico de los 2 archivos
originales):

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado | Archivos donde aparece |
|---|---|---|---|---|---|
| `HTTPSTATUS` (de `curl -w "HTTPSTATUS:%{http_code}"`) | entero | — | código HTTP válido (200-599) | Código de respuesta capturado con el formato de salida `-w` de curl. | Solo los 2 archivos originales (`2026-07-21-cookie-refresh-token.md`, `2026-07-21-cache-libros-ttl.md`). |
| `BODY_BYTES` (de `curl -w "BODY_BYTES:%{size_download}"`) | entero | bytes | ≥ 0 | Tamaño del cuerpo de respuesta capturado con `-w`. | Mismos 2 archivos originales. |
| `HTTP/1.1 <código>` (línea de estado de `curl -i`/`curl -s -D -`) | entero (parte de una línea de texto) | — | código HTTP válido, ej. `200`, `201`, `400`, `403` | Código de respuesta observado directamente en las cabeceras HTTP crudas volcadas por `curl -i`, sin el formato `-w` personalizado. | Los 14 archivos de auditoría OWASP (`2026-07-30-owasp-*.md` en adelante) -- verificado abriendo `2026-07-30-owasp-a01-control-acceso-roto.md` y `2026-07-30-owasp-a03-inyeccion.md` para esta tarea. |

## `docs/mediciones/jacoco/report.csv` (y `report.xml` equivalente)

Reporte de cobertura generado por el plugin Maven `jacoco-maven-plugin`
(`./mvnw clean verify`), no escrito a mano. Columnas verificadas
abriendo el CSV real (`docs/mediciones/jacoco/report.csv`, 51 líneas: 1
cabecera + 50 clases analizadas) para esta tarea -- coincide exactamente
con la lista de columnas que trajo el prompt de esta tarea, sin
discrepancias:

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `GROUP` | string | — | vacío en este reporte (proyecto single-module) | Nombre del grupo/agregado Maven; JaCoCo lo deja vacío cuando el reporte cubre un solo módulo, como es el caso de `backend-springboot`. |
| `PACKAGE` | string (paquete Java completo) | — | ej. `com.uteq.backend.security`, `com.uteq.backend.service` | Paquete Java al que pertenece la clase de esa fila. |
| `CLASS` | string | — | nombre simple de la clase, ej. `LoginRateLimiter`, `JwtService` | Clase Java analizada. |
| `INSTRUCTION_MISSED` / `INSTRUCTION_COVERED` | entero | instrucciones de bytecode | ≥ 0 | Instrucciones de bytecode no cubiertas / cubiertas por al menos un test; la unidad de cobertura más granular que reporta JaCoCo. |
| `BRANCH_MISSED` / `BRANCH_COVERED` | entero | ramas condicionales | ≥ 0 | Ramas de decisión (`if`/`switch`/operadores condicionales) no cubiertas / cubiertas. |
| `LINE_MISSED` / `LINE_COVERED` | entero | líneas de código fuente | ≥ 0 | Líneas fuente no cubiertas / cubiertas -- la base del porcentaje de "cobertura de líneas" citado en el \autoref{cap:resultados} ($81{,}64\,\%$ = `LINE_COVERED` / (`LINE_COVERED` + `LINE_MISSED`), agregado sobre las 50 filas). |
| `COMPLEXITY_MISSED` / `COMPLEXITY_COVERED` | entero | unidades de complejidad ciclomática | ≥ 0 | Complejidad ciclomática no cubierta / cubierta por los tests. |
| `METHOD_MISSED` / `METHOD_COVERED` | entero | métodos | ≥ 0 | Métodos con al menos una instrucción no cubierta / métodos completamente cubiertos. |

## `docs/mediciones/lighthouse/lhci-*.json` (2 corridas)

Salida cruda de Lighthouse CI (`@lhci/cli`), formato JSON estándar de
Lighthouse. Estructura verificada abriendo
`docs/mediciones/lighthouse/lhci-20260731-0300.json` real para esta
tarea -- se documentan solo los campos de nivel superior efectivamente
usados en el \autoref{cap:resultados} (Bloque 4), no los ~20 campos
adicionales del reporte completo (auditorías individuales, huella de
página, etc., no citados en el informe):

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `requestedUrl` / `finalUrl` | string (URL) | — | `http://localhost:4200/` en ambas corridas | URL solicitada y URL final tras redirecciones (idénticas en este proyecto, sin redirecciones). |
| `fetchTime` | timestamp ISO 8601 (UTC) | — | fecha/hora real de la corrida | Momento en que Lighthouse ejecutó la auditoría; ambas corridas reales son del 2026-07-31 (madrugada), no dos días distintos como podría sugerir el propósito original de "3 corridas en días distintos". |
| `configSettings.formFactor` | string (enum) | — | `mobile` en las 2 corridas existentes; `desktop` no ejecutado todavía | Perfil de emulación de dispositivo -- confirma la limitación ya declarada en el \autoref{cap:resultados} de que las 2 corridas existentes son ambas de perfil móvil, ninguna de escritorio. |
| `categories.performance.score` | decimal | proporción 0-1 (se multiplica por 100 al reportar como \%) | observado 0.95-1.0 en estas 2 corridas | Puntaje de la categoría Performance. |
| `categories.accessibility.score` | decimal | proporción 0-1 | observado 0.95-1.0 | Puntaje de la categoría Accessibility. |
| `categories.best-practices.score` | decimal | proporción 0-1 | observado 1.0 en ambas corridas | Puntaje de la categoría Best Practices. |
| `categories.seo.score` | decimal | proporción 0-1 | observado $0.82 \to 1.0$ entre la corrida 1 y la 2 | Puntaje de la categoría SEO -- el único hallazgo real corregido entre ambas corridas, documentado en el \autoref{cap:resultados}. |

## Nota de alcance -- archivos de flujo E2E no reciben sección propia

`docs/mediciones/backend/2026-07-29-flujo-prestamo-devolucion-multa-e2e.md`
y
`docs/mediciones/frontend/2026-07-30-flujo-frontend-prestamos-reservaciones-multas-e2e.md`
(generados en la misma ventana de tiempo que el resto de la evidencia
nueva) **no reciben una sección dedicada** en este diccionario: se
verificó su estructura y usan exactamente la misma cabecera de
`TEMPLATE.md` ya documentada arriba (§`docs/mediciones/sec/*.md`) más
salidas de `curl -i` y de la consola del navegador, sin ningún campo o
formato de dato nuevo que no esté ya cubierto por las tablas de esta
sección. Documentarlos aparte sería repetir la misma tabla sin aportar
un campo nuevo -- decisión de esta tarea, no una omisión.

## `docs/mediciones/sus/sus.csv`

**Datos de prueba (mock/sintéticos) para validación del pipeline de
análisis automatizado — sin valor evidencial.** El bloque de usabilidad
sigue en $N=0$ (ver `OBS-08` en
`docs/observaciones/OBSERVACIONES.md`, reabierta): una corrida declarada
como N=15 se retiró por falta de trazabilidad. La tabla de abajo
documenta el **formato** que tendrá el dataset real (mismos campos que
producirá el export del instrumento), no una recolección ejecutada. Los
participantes de la futura corrida firmarán consentimiento informado
(ver `docs/etica/consentimientos/plantilla.md`); los formularios
firmados nunca se versionarán en este repositorio (ver
`docs/etica/ETHICS.md` §(iii)).

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `codigo` | string | — | `P01` a `P15` | Identificador anónimo del participante. Nunca se incluye nombre, correo ni cédula. |
| `fecha` | string (fecha ISO 8601) | — | `2026-08-28`, `2026-08-29`, `2026-08-30` | Fecha de la sesión de prueba (3 días de recolección). |
| `edad` | entero | años | 18–34 | Rango de edad del participante al momento de la prueba. |
| `sexo` | string | — | `Femenino`, `Masculino` | Sexo autopercibido del participante (opcional en el instrumento; aquí se reporta para descripción demográfica). |
| `experiencia_web` | string | — | `Basica`, `Intermedia`, `Avanzada` | Nivel de experiencia previa con sistemas web similares, autopercibido por el participante. |
| `dispositivo` | string | — | `Laptop`, `Escritorio`, `Movil` | Tipo de dispositivo utilizado durante la sesión de prueba. |
| `Q1` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Creo que me gustaría usar este sistema frecuentemente." (Ítem positivo, contribución: `Q1 - 1`) |
| `Q2` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Encontré el sistema innecesariamente complejo." (Ítem negativo, contribución: `5 - Q2`) |
| `Q3` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Pensé que el sistema era fácil de usar." (Ítem positivo, contribución: `Q3 - 1`) |
| `Q4` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Creo que necesitaría la ayuda de una persona técnica para poder usar este sistema." (Ítem negativo, contribución: `5 - Q4`) |
| `Q5` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Encontré que las diversas funciones de este sistema estaban bien integradas." (Ítem positivo, contribución: `Q5 - 1`) |
| `Q6` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Pensé que había demasiada inconsistencia en este sistema." (Ítem negativo, contribución: `5 - Q6`) |
| `Q7` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "La mayoría de las personas aprenderían a usar este sistema rápidamente." (Ítem positivo, contribución: `Q7 - 1`) |
| `Q8` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Encontré el sistema muy incómodo de usar." (Ítem negativo, contribución: `5 - Q8`) |
| `Q9` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Me sentí muy confiado usando el sistema." (Ítem positivo, contribución: `Q9 - 1`) |
| `Q10` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | "Necesité aprender muchas cosas antes de poder usar este sistema." (Ítem negativo, contribución: `5 - Q10`) |
| `I1` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | Pregunta de interfaz adicional: facilidad de aprendizaje percibida. No forma parte del cálculo SUS estándar. |
| `I2` | entero | Likert 1–5 | 1, 2, 3, 4, 5 | Pregunta de interfaz adicional: confianza para uso independiente. No forma parte del cálculo SUS estándar. |
| `score` | decimal | puntos (0–100) | 0.0–100.0 | Puntuación SUS calculada con la fórmula de Brooke: `((Q1-1)+(5-Q2)+(Q3-1)+(5-Q4)+(Q5-1)+(5-Q6)+(Q7-1)+(5-Q8)+(Q9-1)+(5-Q10)) * 2.5`. Verificado contra el cálculo reproducido por `scripts/sus-analysis.ipynb`. |
| `comentarios` | string | — | texto libre (1 línea) | Comentario cualitativo breve del participante en español. No contiene datos personales identificables. |

**Fórmula de verificación del score:**
```python
score = ((Q1-1) + (5-Q2) + (Q3-1) + (5-Q4) + (Q5-1) +
         (5-Q6) + (Q7-1) + (5-Q8) + (Q9-1) + (5-Q10)) * 2.5
```

## `docs/mediciones/sus/sus_boxplot.svg` y `sus_boxplot.png`

Boxplot generado por `scripts/sus-analysis.ipynb` **sobre los datos mock
de `sus.csv`** (solo validación del pipeline, sin valor evidencial).
Incluye línea de umbral de aceptabilidad (68) y línea de media.

## `docs/mediciones/sus/sus_items_breakdown.svg`

Gráfico de barras horizontal con el promedio de cada ítem SUS (Q1–Q10) en
escala Likert 1–5, generado por `scripts/sus-analysis.ipynb` **sobre los
datos mock**. Ítems impares (positivos) en color verde; ítems pares
(negativos) en naranja.

## Referencias

- `docs/mediciones/README.md` (convención general de evidencia)
- `docs/mediciones/TEMPLATE.md` (plantilla de cada archivo de evidencia)
- `docs/adr/adr-012-cookies-jwt.md`, `docs/adr/adr-008-ttl-cache-libros.md`
- `docs/mediciones/DATA-PROVENANCE.md` (traza cada tabla/figura del
  informe académico hacia el archivo crudo que la origina, complementa
  este diccionario documentando de dónde viene cada dato, no qué forma
  tiene)
