#!/usr/bin/env bash
# ============================================================================
# scripts/validate-traceability.sh
#
# Valida docs/trazabilidad/matriz.csv (A.3.3 de la guia de la Tercera
# Entrega). Reglas exigidas por la guia:
#
#   1. Toda fila con prioridad_moscow=Must debe tener historia_usuario Y
#      caso_de_uso no vacios -- un requisito Must sin trazabilidad hacia su
#      origen (por que existe, quien lo pidio) no es aceptable para la
#      entrega.
#   2. Toda fila con estado=verificado debe tener prueba_automatizada no
#      vacia -- "verificado" implica que existe una prueba real que lo
#      demuestra en cada build, no solo una revision manual puntual (eso
#      es "implementado", ver la columna evidencia_empirica para el
#      historial de verificacion manual).
#
# Uso: scripts/validate-traceability.sh [ruta-al-csv]
# Por defecto valida docs/trazabilidad/matriz.csv desde la raiz del repo.
# Sale con codigo 0 si todo pasa, 1 si encuentra al menos una fila
# invalida (para que el pipeline de CI rechace el commit).
#
# El CSV puede tener campos con comas dentro de valores entrecomillados
# (ej. "AuthServiceTest.foo (3 tests, ver bar)") -- un split ingenuo por
# comas en bash rompe la alineacion de columnas, asi que el parseo real se
# delega al modulo csv de Python (viene preinstalado en los runners de
# GitHub Actions ubuntu-latest, no es una dependencia nueva del proyecto).
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CSV_PATH="${1:-${REPO_ROOT}/docs/trazabilidad/matriz.csv}"

if [ ! -f "${CSV_PATH}" ]; then
    echo "ERROR: no se encontro el archivo de matriz de trazabilidad: ${CSV_PATH}" >&2
    exit 1
fi

python3 - "${CSV_PATH}" <<'PYEOF'
import csv
import sys

csv_path = sys.argv[1]

REQUIRED_COLUMNS = {
    "id_requisito", "tipo", "prioridad_moscow", "historia_usuario",
    "caso_de_uso", "modulo_codigo", "endpoint_api", "prueba_automatizada",
    "tipo_acceso", "evidencia_empirica", "estado",
}


def es_vacio(valor):
    # Un campo se considera vacio si esta en blanco tras recortar espacios.
    # Una nota explicativa como "-- (decision arquitectonica, ver ADR-011)"
    # NO se considera vacio: es contenido real que documenta por que no hay
    # una historia de usuario tradicional, no una celda sin completar.
    return valor is None or valor.strip() == ""


def main():
    with open(csv_path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        faltantes = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if faltantes:
            print(f"ERROR: faltan columnas obligatorias en el CSV: {sorted(faltantes)}", file=sys.stderr)
            sys.exit(1)

        errores = []
        total_filas = 0

        for numero_fila, fila in enumerate(reader, start=2):  # fila 1 = encabezado
            total_filas += 1
            req_id = fila.get("id_requisito", "").strip() or f"(fila {numero_fila} sin id_requisito)"

            if fila.get("prioridad_moscow", "").strip() == "Must":
                if es_vacio(fila.get("historia_usuario")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): prioridad Must pero historia_usuario esta vacio"
                    )
                if es_vacio(fila.get("caso_de_uso")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): prioridad Must pero caso_de_uso esta vacio"
                    )

            if fila.get("estado", "").strip() == "verificado":
                if es_vacio(fila.get("prueba_automatizada")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): estado=verificado pero prueba_automatizada esta vacio "
                        "(un requisito 'verificado' necesita una prueba real, no solo evidencia manual)"
                    )

        if errores:
            print(f"Matriz de trazabilidad invalida: {len(errores)} problema(s) en {total_filas} filas.", file=sys.stderr)
            for err in errores:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

        print(f"Matriz de trazabilidad valida: {total_filas} filas, 0 problemas.")


main()
PYEOF
