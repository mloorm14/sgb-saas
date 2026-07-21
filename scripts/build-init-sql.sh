#!/usr/bin/env bash
# ============================================================================
# scripts/build-init-sql.sh
#
# Concatena db/schema.sql + db/procs/*.sql (orden alfabético) + db/seed.sql,
# en ese orden exacto, en un único db/init/01-consolidado.sql.
#
# PostgreSQL NO recorre subdirectorios de docker-entrypoint-initdb.d/, solo
# ejecuta archivos en su raíz, en orden alfabético (ver
# docs/adr/adr-006-estrategia-schema-reproducible.md). db/procs/ es un
# subdirectorio de db/ — si se montara db/ tal cual, esos 7 archivos nunca
# se ejecutarían. Por eso este script genera un único archivo plano dentro
# de db/init/, y es ESA carpeta (no db/ completo) la que se monta en
# docker-entrypoint-initdb.d/ (ver docker-compose.yml).
#
# db/init/01-consolidado.sql es un artefacto de build (gitignored): se
# regenera en cada `make up`, nunca se edita a mano. Las fuentes de verdad
# siguen siendo db/schema.sql, cada archivo individual de db/procs/ y
# db/seed.sql — para el catálogo, para revisiones, y para que cada proc se
# pueda tocar sin rehacer un archivo enorme a mano.
#
# Orden de concatenación: schema (crea las tablas) -> procs (las funciones
# no requieren que existan filas de catálogo al momento de CREATE, solo al
# invocarse) -> seed (inserta catálogos y datos de ejemplo). El orden
# interno de los archivos de procs/ no importa: son CREATE FUNCTION
# independientes entre sí.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DB_DIR="$ROOT_DIR/db"
SCHEMA_FILE="$DB_DIR/schema.sql"
PROCS_DIR="$DB_DIR/procs"
SEED_FILE="$DB_DIR/seed.sql"
OUT_DIR="$DB_DIR/init"
OUT_FILE="$OUT_DIR/01-consolidado.sql"

# --- Verificación de los 3 orígenes obligatorios ---------------------------
if [ ! -f "$SCHEMA_FILE" ]; then
    echo "ERROR: falta $SCHEMA_FILE — no se puede generar el consolidado." >&2
    exit 1
fi

if [ ! -d "$PROCS_DIR" ] || [ -z "$(ls -A "$PROCS_DIR"/*.sql 2>/dev/null)" ]; then
    echo "ERROR: falta db/procs/ o no contiene archivos *.sql — no se puede generar el consolidado." >&2
    exit 1
fi

if [ ! -f "$SEED_FILE" ]; then
    echo "ERROR: falta $SEED_FILE — no se puede generar el consolidado." >&2
    exit 1
fi

mkdir -p "$OUT_DIR"

{
    echo "-- ============================================================================"
    echo "-- ARCHIVO GENERADO — NO EDITAR A MANO."
    echo "-- Editar las fuentes en db/schema.sql, db/procs/, db/seed.sql y volver a"
    echo "-- correr scripts/build-init-sql.sh."
    echo "-- Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "-- Fuente: db/schema.sql + db/procs/*.sql + db/seed.sql (en ese orden)."
    echo "-- ============================================================================"
    echo

    cat "$SCHEMA_FILE"
    echo

    for proc_file in "$PROCS_DIR"/*.sql; do
        echo
        echo "-- ---------------------------------------------------------------------"
        echo "-- $(basename "$proc_file")"
        echo "-- ---------------------------------------------------------------------"
        cat "$proc_file"
    done
    echo

    cat "$SEED_FILE"
} > "$OUT_FILE"

echo "Generado: $OUT_FILE"
