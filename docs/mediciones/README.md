# docs/mediciones/ — convención de evidencia y mediciones

Esta carpeta reúne toda la evidencia cruda de verificaciones, benchmarks y
análisis exigidos por la guía de la Tercera Entrega (Bloque B.2 y los
bloques que referencian evidencia concreta: A.1, C.1, C.2). Reglas fijas
para que cualquier archivo nuevo cuente como evidencia válida sin depender
de que alguien recuerde el formato cada vez.

## Estructura de subcarpetas

- `docs/mediciones/sec/` — evidencia de seguridad (cookies, cabeceras
  HTTP, hallazgos de auditoría OWASP — Bloque C.2).
- `docs/mediciones/perf/` — resultados de pruebas de carga/rendimiento
  (k6 — Bloque C.1). Aún no tiene archivos: se crea con la primera
  corrida real (`make bench` referencia esta carpeta desde ya).
- Otras subcarpetas se agregan según el tipo de medición (ej. `docs/mediciones/sus/`
  si se hace una encuesta System Usability Scale), siguiendo el mismo
  criterio: una subcarpeta por tipo de evidencia, no todo suelto en la raíz.

## Cada archivo nuevo debe partir de `TEMPLATE.md`

Copiar `docs/mediciones/TEMPLATE.md` como punto de partida — no escribir un
archivo de evidencia desde cero improvisando la estructura. Las secciones
obligatorias son: cabecera, propósito, metodología/comando ejecutado,
resultados crudos, análisis breve (ver el propio `TEMPLATE.md` para el
detalle de qué va en cada una).

## Cabecera obligatoria: generarla con el script, no a mano

Todo archivo de evidencia debe empezar con el bloque que genera
`scripts/mediciones-header.sh` (fecha ISO 8601 UTC real, commit hash corto,
y versiones exactas de Docker/Docker Compose/Java/Maven/PostgreSQL/Redis):

```bash
./scripts/mediciones-header.sh >> docs/mediciones/<subcarpeta>/<archivo>.md
```

Esto reemplaza el proceso manual que se usó para los dos primeros archivos
de `docs/mediciones/sec/` (escritos antes de que este script existiera, y
ya retro-ajustados a este mismo formato). No completar la cabecera a mano
ni copiar/pegar una de otro archivo — cada corrida debe generar la suya con
el commit y timestamp reales de ESA corrida.

## Convención de nombres de archivo

- **Markdown de evidencia/verificación puntual** (cookies, cache, hallazgos
  de auditoría, cualquier `curl`/`psql`/`redis-cli` documentado):
  `YYYY-MM-DD-descripcion-corta.md`, fecha en UTC del día de la corrida,
  descripción en minúsculas con guiones. Ejemplos ya existentes:
  `docs/mediciones/sec/2026-07-21-cookie-refresh-token.md`,
  `docs/mediciones/sec/2026-07-21-cache-libros-ttl.md`.
- **Corridas de k6 (Bloque C.1)**: `docs/mediciones/perf/kNN-runM.json`,
  donde `NN` identifica el escenario de prueba (ej. `k01` = smoke test
  50 VUs/30s, `k02` = siguiente escenario que se defina) y `M` el número de
  repetición de esa misma corrida (`run1`, `run2`, `run3`...) — varias
  corridas del mismo escenario permiten comparar variabilidad, por lo que
  nunca se sobrescribe una corrida anterior con el mismo nombre. El resumen
  humano de cada corrida (interpretación, no solo el JSON crudo de k6) va en
  un `.md` hermano con el mismo prefijo, ej. `k01-run1.md`, siguiendo
  igualmente `TEMPLATE.md`.

## Semilla fija obligatoria para cualquier dato aleatorio o simulado

Cualquier script que genere datos de prueba aleatorios o simulados (carga
de datos ficticios, escenarios de SUS simulados, selección aleatoria de
casos de prueba, etc.) **debe fijar una semilla explícita y documentarla en
el propio script**, no solo mencionarla en el archivo de resultados — sin
esto, dos corridas del mismo script producen datos distintos y el
resultado deja de ser reproducible.

- Python: `random.seed(42)` / `numpy.random.seed(42)` al inicio del script,
  con un comentario indicando por qué 42 (o el valor elegido) y que debe
  mantenerse fijo entre corridas comparables.
- k6 (JavaScript): k6 no tiene un flag `--seed` nativo para su generador de
  números aleatorios; si un script de k6 necesita aleatoriedad (ej. elegir
  entre varios endpoints con probabilidad), implementar un PRNG determinista
  simple sembrado con una constante fija en el propio script y documentarlo
  ahí — no depender de `Math.random()` sin sembrar.
- Cualquier otra herramienta: usar el mecanismo de semilla que exponga
  (`--seed 42`, `RANDOM_SEED=42`, etc.) y documentarlo en un comentario
  junto a donde se usa, no solo en este README.

El valor de la semilla en sí (42, u otro) no importa tanto como que sea
**fijo, explícito y documentado en el script** — así cualquiera puede
reproducir exactamente la misma corrida.

## Qué NO va en `docs/mediciones/`

- Decisiones de diseño o justificaciones arquitectónicas — eso va en
  `docs/adr/` (Architecture Decision Records), no aquí. `docs/mediciones/`
  es evidencia cruda de que algo se verificó, no el razonamiento de por qué
  se construyó de una forma u otra.
- Observaciones de proceso/equipo — eso va en `docs/observaciones/`.
