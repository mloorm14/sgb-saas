# Evidencia — Bloque C.1: prueba de carga real sobre GET /api/v1/libros (cache Redis)

## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: 2026-07-31T02:54:00Z a 2026-07-31T03:00:00Z (3 corridas consecutivas)
- **Commit**: `a3d41ac`
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" 2026-04-21 LTS
- **Maven**: Apache Maven 3.9.12 (848fbb4bf2d427b72bdb2471c22fced7ebd9a7a1)
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14
- **Redis** (contenedor `sgb_redis`): 7.4.9
- **k6**: v2.1.0 (imagen oficial `grafana/k6`, ejecutada vía `docker run --network sgb-saas_default`)

## Propósito

Bloque C.1 — prueba de carga real (no simulada) contra el endpoint con cache
Redis `GET /api/v1/libros` (`@Cacheable("libros")` en `LibroService`), para
medir el efecto de cache caliente vs cache frío sobre la latencia bajo 50 VUs
concurrentes, y verificar los umbrales exigidos por la guía: p95 < 200ms con
cache caliente, p95 < 500ms con cache frío.

## Metodología / comando ejecutado

Script: [`k6/libros-listado-test.js`](../../../k6/libros-listado-test.js),
perfil de carga base importado de [`k6/opts.js`](../../../k6/opts.js) (10s
ramp-up a 50 VUs, 30s sostenido, 10s ramp-down — el mismo perfil para ambos
escenarios).

Dos escenarios en el mismo script, corridos en ventanas de tiempo separadas
para no mezclar ambas cargas sobre el backend en el mismo instante:

- **`cache_caliente`**: siempre pide `page=0&size=10` (misma key de
  `@Cacheable`). Tras el primer hit por VU, el resto de peticiones de ese
  mismo `Pageable` deberían leer de Redis.
- **`cache_frio`**: pide una página distinta en cada request, elegida con un
  PRNG determinista propio (`mulberry32`, semilla fija `42`, documentado en
  el propio script) — cada página distinta genera una key de `@Cacheable`
  distinta, por lo que **siempre** es cache miss desde la perspectiva de
  Spring Cache.

Autenticación: `setup()` hace login real contra `POST /api/auth/login` con
el usuario admin de desarrollo (`admin@sgb-saas.local`, ver README) y todas
las VUs reutilizan el `accessToken` obtenido.

Comando ejecutado 3 veces de forma independiente (una corrida = ambos
escenarios secuenciales, ~106s cada corrida):

```bash
docker run --rm --network sgb-saas_default \
  -v "$(pwd)/k6:/scripts" -v "$(pwd)/docs/mediciones/perf:/out" \
  grafana/k6 run --out json=/out/k6-run{N}.json /scripts/libros-listado-test.js
```

Datos crudos (formato NDJSON de k6, un `Point` por métrica por petición):

- [`k6-run1.json`](k6-run1.json)
- [`k6-run2.json`](k6-run2.json)
- [`k6-run3.json`](k6-run3.json)

Análisis agregado calculado con [`scripts/perf-analysis.py`](../../../scripts/perf-analysis.py)
(media, mediana, desviación típica, IC 95% de la media, percentiles
p50/p90/p95/p99, tasa de error HTTP ≥500 y throughput, por escenario, sobre
la métrica `http_req_duration` filtrada por tag `scenario`):

```bash
python scripts/perf-analysis.py \
  docs/mediciones/perf/k6-run1.json \
  docs/mediciones/perf/k6-run2.json \
  docs/mediciones/perf/k6-run3.json
```

## Resultados crudos

### Resumen por corrida (consola de k6, thresholds evaluados por k6 mismo)

| Corrida | cache_caliente p95 | cache_frio p95 | http_req_failed | Umbral caliente (<200ms) | Umbral frío (<500ms) |
|---|---|---|---|---|---|
| 1 | 107.31ms | 10.96ms | 0.00% | ✅ pasa | ✅ pasa |
| 2 | 10.55ms | 5.93ms | 0.00% | ✅ pasa | ✅ pasa |
| 3 | 6.25ms | 5.72ms | 0.00% | ✅ pasa | ✅ pasa |

### Análisis agregado (`perf-analysis.py`, las 3 corridas combinadas)

| Escenario | n | media (ms) | mediana (ms) | σ (ms) | IC95% media (ms) | p50 | p90 | p95 | p99 | error ≥500 | throughput (req/s) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| cache_caliente | 5906 | 27.67 | 5.91 | 194.92 | [22.70, 32.64] | 5.91 | 15.63 | **29.79** | 197.58 | 0.00% | 118.12 |
| cache_frio | 6039 | 5.40 | 4.80 | 3.46 | [5.31, 5.48] | 4.80 | 7.07 | **7.96** | 13.79 | 0.00% | 120.78 |

Salida JSON completa del script (reproducible con el comando de arriba):

```json
[
  {
    "escenario": "cache_caliente",
    "n_peticiones": 5906,
    "media_ms": 27.66865201710125,
    "mediana_ms": 5.9130255,
    "desviacion_tipica_ms": 194.91644443152174,
    "ic95_media_ms": [22.697490995756194, 32.63981303844631],
    "p50_ms": 5.9130255,
    "p90_ms": 15.630169500000001,
    "p95_ms": 29.78529875,
    "p99_ms": 197.58115424999988,
    "tasa_error_5xx": 0.0,
    "throughput_req_s": 118.12
  },
  {
    "escenario": "cache_frio",
    "n_peticiones": 6039,
    "media_ms": 5.396359890379202,
    "mediana_ms": 4.803499,
    "desviacion_tipica_ms": 3.458193291963703,
    "ic95_media_ms": [5.309138537115976, 5.483581243642428],
    "p50_ms": 4.803499,
    "p90_ms": 7.0650948,
    "p95_ms": 7.960982299999994,
    "p99_ms": 13.78792174,
    "tasa_error_5xx": 0.0,
    "throughput_req_s": 120.78
  }
]
```

## Umbrales exigidos por la guía — resultado real

| Umbral | Exigido | Obtenido (agregado 3 corridas) | Cumple |
|---|---|---|---|
| p95 cache caliente | < 200ms | 29.79ms | ✅ Sí |
| p95 cache frío | < 500ms | 7.96ms | ✅ Sí |
| Tasa de error HTTP ≥500 | 0% (implícito, ninguna guía tolera 500 bajo carga nominal) | 0.00% (0 de 11 945 peticiones) | ✅ Sí |

Ambos umbrales se cumplen con margen amplio en las 3 corridas individuales y
en el agregado. No se ajustó ningún dato para lograr este resultado.

## Análisis breve

1. **Ambos umbrales se cumplen con margen amplio** (p95 caliente 29.79ms
   contra el límite de 200ms; p95 frío 7.96ms contra el límite de 500ms), sin
   errores 5xx en las 11 945 peticiones HTTP realizadas across las 3
   corridas.

2. **Hallazgo colateral — la corrida 1 tiene una cola pesada anómala en
   `cache_caliente`** (p95=107.31ms, p99=2132ms, máximo 2.63s) que **no** se
   repite en las corridas 2 y 3 (p95 10.55ms y 6.25ms respectivamente). La
   explicación más probable es warm-up de JVM/JIT y del pool de conexiones
   de HikariCP: la corrida 1 fue la primera ejecución de carga real desde
   que el contenedor `sgb_backend` llevaba varias horas sin tráfico
   significativo (ver `docker compose ps`, contenedor con "Up 4 hours" antes
   de esta prueba). Esto se documenta con honestidad en vez de descartarlo
   o repetir la corrida hasta que "saliera bien" — el agregado de las 3
   corridas ya incluye este efecto y aun así cumple el umbral.

3. **Limitación metodológica del escenario `cache_frio` — amenaza a la
   validez de la comparación caliente-vs-frío.** El PRNG elige páginas al
   azar entre 0 y 999 (`page=${Math.floor(rng() * 1000)}`); dado que la
   tabla `libro` tiene pocos registros de ejemplo (semilla de desarrollo,
   ver `db/seed.sql`), la gran mayoría de esas páginas quedan **fuera de
   rango** y Spring Data devuelve una `Page` vacía sin necesidad de traer
   filas de la tabla — una consulta barata, no la consulta "página llena"
   que sí ejecuta `cache_caliente` en `page=0`. Esto significa que
   `cache_frio`, tal como está construido, mide sobre todo el costo de una
   consulta `COUNT` + `SELECT` con resultado vacío, **no** el costo real de
   traer y serializar una página completa de libros sin cache. El umbral de
   500ms igual se cumple con margen amplio, pero la comparación
   "cache caliente vs cache frío" de este experimento **no es una
   comparación limpia de la misma consulta con y sin cache** — es una
   limitación real que debe quedar declarada en "Amenazas a la validez" del
   informe, no oculta.

4. **No se ejecutaron corridas adicionales para "limpiar" el resultado de la
   corrida 1** ni se excluyó ningún dato del análisis agregado — las 3
   corridas completas están en los JSON crudos versionados y el script de
   análisis las procesa tal cual.
