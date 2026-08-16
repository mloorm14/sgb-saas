#!/usr/bin/env bash
# ============================================================================
# scripts/capture-versions.sh â€” D.2: captura las versiones REALES del entorno
# de desarrollo y las vuelca a docs/entorno/versions.txt.
#
# Que documenta y por que (literal del criterio D.2: "el entorno de
# desarrollo queda documentado, actualizado con cada release"):
#   - docker --version / docker compose version: el engine y el orquestador
#     con los que se levanta todo el stack (make up).
#   - java -version: el JDK con el que se compilan y corren los tests del
#     backend (spring-boot-maven-plugin y mvnw exigen un JDK 21 local).
#   - node --version y la version de Angular CLI (npx ng version desde
#     frontend-angular/, mismo entry point que usa make test-frontend).
#   - python3 --version: el interprete con el que corre scripts/perf-analysis.py
#     (make docs / make bench).
#   - k6: version de la imagen grafana/k6 via 'docker run grafana/k6 version'
#     -- exactamente igual que la invoca el target bench del Makefile (k6
#     NO es un binario local en este proyecto, siempre corre como contenedor).
#   - Imagen base del contenedor del backend: las dos etapas del Dockerfile
#     (build: eclipse-temurin:21-jdk-alpine; runtime: eclipse-temurin alpin,
#     ambas pinadas por digest sha256). Se documenta la version del
#     CONTENEDOR (la que corre el sistema), no la del host donde corre este
#     script -- el JDK del host se usa en test-backend pero el runtime real
#     de produccion/desarrollo es el de esa imagen.
#
# Reglas de comportamiento:
#   - Idempotente: cada corrida SOBRESCRIBE docs/entorno/versions.txt
#     completo (timestamp ISO 8601 nuevo) -- no acumula corridas. D.2 pide
#     el estado del entorno actualizado, no un historial.
#   - Termina con exit code 0 solo si logro capturar TODAS las herramientas
#     obligatorias (las que make all/bench/docs realmente usan). Si alguna
#     falta, imprime el error claro pero sigue capturando el resto (no
#     aborta a medias) y termina con exit code 1.
#   - La captura del Java real DENTRO del contenedor backend
#     ('docker exec sgb_backend java -version') es best-effort: si el stack
#     no esta levantado en este momento, se registra la imagen base pinada
#     del Dockerfile sin fallar el script (esa es la fuente de verdad del
#     runtime, y no vale la pena levantar el stack solo para esto).
# ============================================================================

set -uo pipefail

OUT_DIR="docs/entorno"
OUT_FILE="$OUT_DIR/versions.txt"
REQUIRED_FAILED=0

mkdir -p "$OUT_DIR"

# Nuevo archivo en cada corrida: nunca concatenar sobre corridas previas.
: > "$OUT_FILE"

cap() {
    # cap <titulo> <comando...>  -> captura salida real (stdout+stderr).
    # Primera linea de salida = pie de la version; el resto queda igual.
    local titulo="$1"
    shift
    printf '\n### %s\n' "$titulo" >> "$OUT_FILE"
    local salida
    if salida=$("$@" 2>&1); then
        printf '%s\n' "$salida" >> "$OUT_FILE"
    else
        printf '%s\n' "$salida" >> "$OUT_FILE"
        printf 'ERROR: la herramienta no esta disponible o fallo al ejecutar en este entorno: %s\n' "$*" \
            | tee -a "$OUT_FILE" >&2
        REQUIRED_FAILED=1
    fi
}

# ----------------------------------------------------------------------------
# Cabecera: timestamp ISO 8601 (UTC) del momento exacto de la captura.
# ----------------------------------------------------------------------------
{
    echo "# Entorno de desarrollo de SGB-SaaS (captura automatica)"
    echo
    echo "- Fecha de captura (ISO 8601, UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "- Generado por: scripts/capture-versions.sh (invocado por 'make docs' / 'make all')"
    echo "- Documenta el estado del entorno en que corrio la medicion/entrega,"
    echo "  para reproducirla (criterio D.2 de la guia)."
} >> "$OUT_FILE"

# ----------------------------------------------------------------------------
# Herramientas obligatorias (make up/test/bench/docs las usan todas).
# ----------------------------------------------------------------------------
echo "### Docker CLI" >> "$OUT_FILE"
docker --version >> "$OUT_FILE" 2>&1 || { echo "ERROR: docker no disponible" >&2; REQUIRED_FAILED=1; }

echo "### Docker Compose" >> "$OUT_FILE"
docker compose version >> "$OUT_FILE" 2>&1 || { echo "ERROR: docker compose no disponible" >&2; REQUIRED_FAILED=1; }

# java imprime la version en stderr (JVM log), no en stdout -- se captura
# con 2>&1 como todas las demas.
echo "### Java (JDK del host, usado por test-backend)" >> "$OUT_FILE"
java -version >> "$OUT_FILE" 2>&1 || { echo "ERROR: java no disponible o no es Java 21+ (mvnw lo exige)" >&2; REQUIRED_FAILED=1; }

# version de k6 via Docker, igual que el target bench del Makefile:
#   docker run --rm grafana/k6 run ...   (k6 nunca se instala localmente)
echo "### k6 (imagen grafana/k6, via docker run -- igual que make bench)" >> "$OUT_FILE"
docker run --rm grafana/k6 version >> "$OUT_FILE" 2>&1 \
    || { echo "ERROR: no se pudo obtener la version de k6 (docker run grafana/k6 version)" >&2; REQUIRED_FAILED=1; }

if command -v node >/dev/null 2>&1; then
    echo "### Node.js" >> "$OUT_FILE"
    node --version >> "$OUT_FILE" 2>&1
else
    echo "### Node.js" >> "$OUT_FILE"
    echo "ERROR: node no esta disponible en este entorno" >> "$OUT_FILE"
    REQUIRED_FAILED=1
fi

# Angular CLI: primero npx desde frontend-angular/ (entry point real de
# make test-frontend); si no hay node_modules local, npx descargaria la CLI
# con red -- por eso --no-install: solo se usa la version que ya existe en
# el proyecto. Si ni asi esta, se intenta 'ng version' global.
if command -v npx >/dev/null 2>&1 && { (cd frontend-angular && npx --no-install ng version) >/dev/null 2>&1; }; then
    echo "### Angular CLI (frontend-angular, via npx)" >> "$OUT_FILE"
    (cd frontend-angular && npx --no-install ng version) >> "$OUT_FILE" 2>&1
elif command -v ng >/dev/null 2>&1; then
    echo "### Angular CLI (global)" >> "$OUT_FILE"
    NO_COLOR=1 ng version >> "$OUT_FILE" 2>&1
else
    echo "### Angular CLI" >> "$OUT_FILE"
    echo "ERROR: Angular CLI no disponible (ni npx --no-install ng version en frontend-angular/ ni ng global)." >> "$OUT_FILE"
    echo "        Ejecutar 'cd frontend-angular && npm ci' primero (mismo paso que hace CI)." >> "$OUT_FILE"
    REQUIRED_FAILED=1
fi

if command -v python3 >/dev/null 2>&1; then
    echo "### Python (usado por scripts/perf-analysis.py)" >> "$OUT_FILE"
    python3 --version >> "$OUT_FILE" 2>&1
    echo >> "$OUT_FILE"
    echo "Modulos usados por perf-analysis.py:" >> "$OUT_FILE"
    python3 -c "import scipy, matplotlib; print('  scipy', scipy.__version__); print('  matplotlib', matplotlib.__version__)" >> "$OUT_FILE" 2>&1 \
        || echo "  (sin scipy/matplotlib: perf-analysis.py cae a su implementacion manual pura-Python)" >> "$OUT_FILE"
else
    echo "### Python" >> "$OUT_FILE"
    echo "ERROR: python3 no esta disponible (lo usa scripts/perf-analysis.py)" >> "$OUT_FILE"
    REQUIRED_FAILED=1
fi

# ----------------------------------------------------------------------------
# Imagen base del contenedor backend (la que realmente corre el sistema).
# Se documenta la del Dockerfile (pinada por digest sha256, ver
# docs/DIGESTS-LOG.md), NO la del host. Si el contenedor esta levantado se
# captura ademas el Java real dentro de el (best-effort, no falla el script
# si el stack no esta arriba en este momento).
# ----------------------------------------------------------------------------
{
    echo "### Imagen base del contenedor backend (backend-springboot/Dockerfile)"
    echo "Las dos etapas estan pinadas por digest sha256 (ver docs/DIGESTS-LOG.md):"
    grep -E "FROM eclipse-temurin@" backend-springboot/Dockerfile
    echo
} >> "$OUT_FILE"

if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'sgb_backend'; then
    {
        echo "### Java real dentro del contenedor backend (sgb_backend)"
        docker exec sgb_backend java -version 2>&1 >> "$OUT_FILE" \
            || echo "ERROR interno: no se pudo ejecutar java dentro de sgb_backend (se mantiene lo documentado del Dockerfile)."
    }
fi

# ----------------------------------------------------------------------------
# Cierre: resumen de estado.
# ----------------------------------------------------------------------------
echo
if [ "$REQUIRED_FAILED" -ne 0 ]; then
    echo "ERROR: capture-versions.sh: al menos una herramienta obligatoria no esta disponible en este"
    echo "entorno (ver mensajes de ERROR arriba). El archivo docs/entorno/versions.txt se genero"
    echo "igual con el resto de la informacion, pero este script NO puede reportar exito: la"
    echo "evidencia D.2 debe reflejar un entorno que pueda correr make all de punta a punta."
    echo "Si hace falta, consultar README.md (seccion de requisitos previos) para completar."
    exit 1
fi

echo "OK: docs/entorno/versions.txt actualizado con todas las versiones del entorno."
exit 0