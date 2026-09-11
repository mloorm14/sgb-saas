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
#   3. Todo id_requisito de la matriz debe existir como encabezado
#      "#### REQ-F-XXX -- titulo" / "##### REQ-NF-XXX -- titulo" en el SRS,
#      y viceversa (todo ID que aparezca como encabezado en el SRS debe
#      existir como fila en la matriz) -- agregado 2026-09-07 (revision
#      Dr. Guerrero, M23): ambos documentos deben quedar sincronizados,
#      nunca uno solo de los dos actualizado (ver seccion 4 del SRS y
#      ADR-013 para el mismo riesgo ya documentado con Flyway/schema.sql).
#   4. Todo valor no vacio de historia_usuario/caso_de_uso debe
#      corresponder a un archivo real en docs/requisitos/historias/ o
#      docs/requisitos/casos-de-uso/ respectivamente -- agregado
#      2026-09-07 (M23). Excepciones que NO se validan como archivo:
#      - una nota de decision arquitectonica sin ID real (ej.
#        "N/A - decision arquitectonica", "-- (ver ADR-011)"): se detecta
#        porque no contiene ningun token con forma de ID HU-/CU-.
#      - los IDs legados del modulo de Cajas (HU-01..HU-05, CU-01..CU-05):
#        viven consolidados en docs/requisitos/historias-usuario.md y
#      docs/requisitos/casos-de-uso.md, no como archivo individual (ver
#      seccion 6, punto 8 del SRS) -- se valida que el ID aparezca
#      dentro de esos dos archivos consolidados en vez de exigir un
#      archivo propio.
#   5. La columna "estado" solo puede tomar uno de los 3 valores del
#      vocabulario declarado: "implementado", "verificado", "pendiente".
#      Cualquier otro valor (incluyendo matizadores como parentesis) es
#      error. Los matizadores deben ir en la columna "observaciones".
#
# Uso: scripts/validate-traceability.sh [ruta-al-csv] [ruta-al-srs]
# Por defecto valida docs/trazabilidad/matriz.csv y docs/requisitos/SRS.md
# desde la raiz del repo.
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
SRS_PATH="${2:-${REPO_ROOT}/docs/requisitos/SRS.md}"
HISTORIAS_DIR="${REPO_ROOT}/docs/requisitos/historias"
CASOS_USO_DIR="${REPO_ROOT}/docs/requisitos/casos-de-uso"
HISTORIAS_LEGADO="${REPO_ROOT}/docs/requisitos/historias-usuario.md"
CASOS_USO_LEGADO="${REPO_ROOT}/docs/requisitos/casos-de-uso.md"

if [ ! -f "${CSV_PATH}" ]; then
    echo "ERROR: no se encontro el archivo de matriz de trazabilidad: ${CSV_PATH}" >&2
    exit 1
fi

if [ ! -f "${SRS_PATH}" ]; then
    echo "ERROR: no se encontro el SRS: ${SRS_PATH}" >&2
    exit 1
fi

python3 - "${CSV_PATH}" "${SRS_PATH}" "${HISTORIAS_DIR}" "${CASOS_USO_DIR}" "${HISTORIAS_LEGADO}" "${CASOS_USO_LEGADO}" <<'PYEOF'
import csv
import os
import re
import sys

csv_path = sys.argv[1]
srs_path = sys.argv[2]
historias_dir = sys.argv[3]
casos_uso_dir = sys.argv[4]
historias_legado_path = sys.argv[5]
casos_uso_legado_path = sys.argv[6]

ID_REQUISITO_RE = re.compile(r"^#{4,5}\s+(REQ-(?:F|NF)-\d+[a-z]?)\b")
ID_HU_CU_RE = re.compile(r"\b((?:HU|CU)-[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)\b")
# IDs legados de Cajas: consolidados en un solo archivo por tipo (historias-usuario.md /
# casos-de-uso.md), no un archivo por HU/CU como el resto de modulos (ver SRS seccion 6.8).
IDS_LEGADO_CAJAS = {f"HU-0{n}" for n in range(1, 6)} | {f"CU-0{n}" for n in range(1, 6)}

# Vocabulario valido para la columna "estado" (M1)
ESTADOS_VALIDOS = {"implementado", "verificado", "pendiente"}


def ids_requisito_en_srs(path):
    ids = set()
    with open(path, encoding="utf-8") as f:
        for linea in f:
            m = ID_REQUISITO_RE.match(linea)
            if m:
                ids.add(m.group(1))
    return ids


def archivo_existe_con_prefijo(directorio, id_):
    # El nombre real es "<ID>-<slug>.md" -- se busca por prefijo exacto
    # "<ID>-" para no confundir HU-01 con un futuro HU-011.
    if not os.path.isdir(directorio):
        return False
    prefijo = id_ + "-"
    return any(nombre.startswith(prefijo) and nombre.endswith(".md")
               for nombre in os.listdir(directorio))


def id_existe_en_archivo_legado(path, id_):
    if not os.path.isfile(path):
        return False
    with open(path, encoding="utf-8") as f:
        contenido = f.read()
    return id_ in contenido

REQUIRED_COLUMNS = {
    "id_requisito", "tipo", "prioridad_moscow", "historia_usuario",
    "caso_de_uso", "modulo_codigo", "endpoint_api", "prueba_automatizada",
    "tipo_acceso", "evidencia_empirica", "estado", "observaciones",
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
        ids_matriz = set()

        for numero_fila, fila in enumerate(reader, start=2):  # fila 1 = encabezado
            total_filas += 1
            req_id = fila.get("id_requisito", "").strip() or f"(fila {numero_fila} sin id_requisito)"
            if req_id:
                ids_matriz.add(req_id)

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

            # Validacion 5 (M1): estado debe ser uno de los 3 valores validos
            estado = fila.get("estado", "").strip()
            if estado and estado not in ESTADOS_VALIDOS:
                errores.append(
                    f"{req_id} (fila {numero_fila}): estado '{estado}' no es valido. "
                    f"Valores permitidos: {', '.join(sorted(ESTADOS_VALIDOS))}. "
                    f"Los matizadores deben ir en la columna 'observaciones'."
                )

            # Validacion 2: estado=verificado requiere prueba_automatizada
            if fila.get("estado", "").strip() == "verificado":
                if es_vacio(fila.get("prueba_automatizada")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): estado=verificado pero prueba_automatizada esta vacio "
                        "(un requisito 'verificado' necesita una prueba real, no solo evidencia manual)"
                    )

            # Validacion 4 (M23): historia_usuario/caso_de_uso deben apuntar a
            # archivos reales cuando el valor tiene forma de ID HU-/CU-.
            for columna, directorio, tipo in (
                ("historia_usuario", historias_dir, "HU"),
                ("caso_de_uso", casos_uso_dir, "CU"),
            ):
                valor = fila.get(columna) or ""
                for id_encontrado in ID_HU_CU_RE.findall(valor):
                    if not id_encontrado.startswith(tipo + "-"):
                        continue  # ej. no validar un CU- encontrado dentro de la columna historia_usuario
                    if id_encontrado in IDS_LEGADO_CAJAS:
                        legado_path = historias_legado_path if tipo == "HU" else casos_uso_legado_path
                        if not id_existe_en_archivo_legado(legado_path, id_encontrado):
                            errores.append(
                                f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                                f"(ID legado de Cajas) pero no aparece en {os.path.relpath(legado_path)}"
                            )
                        continue
                    if not archivo_existe_con_prefijo(directorio, id_encontrado):
                        errores.append(
                            f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                            f"pero no existe ningun archivo '{id_encontrado}-*.md' en {os.path.relpath(directorio)}"
                        )

        # Validacion 3 (M23): IDs de requisito sincronizados entre la matriz y el SRS.
        ids_srs = ids_requisito_en_srs(srs_path)
        solo_en_matriz = sorted(ids_matriz - ids_srs)
        solo_en_srs = sorted(ids_srs - ids_matriz)
        for id_ in solo_en_matriz:
            errores.append(
                f"{id_}: existe en la matriz pero no tiene encabezado '#### {id_}' / '##### {id_}' en {os.path.relpath(srs_path)}"
            )
        for id_ in solo_en_srs:
            errores.append(
                f"{id_}: tiene encabezado en {os.path.relpath(srs_path)} pero no existe ninguna fila en la matriz"
            )

        if errores:
            print(f"Matriz de trazabilidad invalida: {len(errores)} problema(s) en {total_filas} filas.", file=sys.stderr)
            for err in errores:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

        print(f"Matriz de trazabilidad valida: {total_filas} filas, 0 problemas.")


def main():
    with open(csv_path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        faltantes = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if faltantes:
            print(f"ERROR: faltan columnas obligatorias en el CSV: {sorted(faltantes)}", file=sys.stderr)
            sys.exit(1)

        errores = []
        total_filas = 0
        ids_matriz = set()

        for numero_fila, fila in enumerate(reader, start=2):  # fila 1 = encabezado
            total_filas += 1
            req_id = fila.get("id_requisito", "").strip() or f"(fila {numero_fila} sin id_requisito)"
            if req_id:
                ids_matriz.add(req_id)

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

            # Validacion 5 (M1): estado debe ser uno de los 3 valores validos
            estado = fila.get("estado", "").strip()
            if estado and estado not in ESTADOS_VALIDOS:
                errores.append(
                    f"{req_id} (fila {numero_fila}): estado '{estado}' no es valido. "
                    f"Valores permitidos: {', '.join(sorted(ESTADOS_VALIDOS))}. "
                    f"Los matizadores deben ir en la columna 'observaciones'."
                )

            # Validacion 2: estado=verificado requiere prueba_automatizada
            if fila.get("estado", "").strip() == "verificado":
                if es_vacio(fila.get("prueba_automatizada")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): estado=verificado pero prueba_automatizada esta vacio "
                        "(un requisito 'verificado' necesita una prueba real, no solo evidencia manual)"
                    )

            # Validacion 4 (M23): historia_usuario/caso_de_uso deben apuntar a
            # archivos reales cuando el valor tiene forma de ID HU-/CU-.
            for columna, directorio, tipo in (
                ("historia_usuario", historias_dir, "HU"),
                ("caso_de_uso", casos_uso_dir, "CU"),
            ):
                valor = fila.get(columna) or ""
                for id_encontrado in ID_HU_CU_RE.findall(valor):
                    if not id_encontrado.startswith(tipo + "-"):
                        continue  # ej. no validar un CU- encontrado dentro de la columna historia_usuario
                    if id_encontrado in IDS_LEGADO_CAJAS:
                        legado_path = historias_legado_path if tipo == "HU" else casos_uso_legado_path
                        if not id_existe_en_archivo_legado(legado_path, id_encontrado):
                            errores.append(
                                f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                                f"(ID legado de Cajas) pero no aparece en {os.path.relpath(legado_path)}"
                            )
                        continue
                    if not archivo_existe_con_prefijo(directorio, id_encontrado):
                        errores.append(
                            f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                            f"pero no existe ningun archivo '{id_encontrado}-*.md' en {os.path.relpath(directorio)}"
                        )

        # Validacion 3 (M23): IDs de requisito sincronizados entre la matriz y el SRS.
        ids_srs = ids_requisito_en_srs(srs_path)
        solo_en_matriz = sorted(ids_matriz - ids_srs)
        solo_en_srs = sorted(ids_srs - ids_matriz)
        for id_ in solo_en_matriz:
            errores.append(
                f"{id_}: existe en la matriz pero no tiene encabezado '#### {id_}' / '##### {id_}' en {os.path.relpath(srs_path)}"
            )
        for id_ in solo_en_srs:
            errores.append(
                f"{id_}: tiene encabezado en {os.path.relpath(srs_path)} pero no existe ninguna fila en la matriz"
            )

        if errores:
            print(f"Matriz de trazabilidad invalida: {len(errores)} problema(s) en {total_filas} filas.", file=sys.stderr)
            for err in errores:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

        print(f"Matriz de trazabilidad valida: {total_filas} filas, 0 problemas.")


def main():
    with open(csv_path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        faltantes = REQUIRED_COLUMNS - set(reader.fieldnames or [])
        if faltantes:
            print(f"ERROR: faltan columnas obligatorias en el CSV: {sorted(faltantes)}", file=sys.stderr)
            sys.exit(1)

        errores = []
        total_filas = 0
        ids_matriz = set()

        for numero_fila, fila in enumerate(reader, start=2):  # fila 1 = encabezado
            total_filas += 1
            req_id = fila.get("id_requisito", "").strip() or f"(fila {numero_fila} sin id_requisito)"
            if req_id:
                ids_matriz.add(req_id)

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

            # Validacion 5 (M1): estado debe ser uno de los 3 valores validos
            estado = fila.get("estado", "").strip()
            if estado and estado not in ESTADOS_VALIDOS:
                errores.append(
                    f"{req_id} (fila {numero_fila}): estado '{estado}' no es valido. "
                    f"Valores permitidos: {', '.join(sorted(ESTADOS_VALIDOS))}. "
                    f"Los matizadores deben ir en la columna 'observaciones'."
                )

            # Validacion 2: estado=verificado requiere prueba_automatizada
            if fila.get("estado", "").strip() == "verificado":
                if es_vacio(fila.get("prueba_automatizada")):
                    errores.append(
                        f"{req_id} (fila {numero_fila}): estado=verificado pero prueba_automatizada esta vacio "
                        "(un requisito 'verificado' necesita una prueba real, no solo evidencia manual)"
                    )

            # Validacion 4 (M23): historia_usuario/caso_de_uso deben apuntar a
            # archivos reales cuando el valor tiene forma de ID HU-/CU-.
            for columna, directorio, tipo in (
                ("historia_usuario", historias_dir, "HU"),
                ("caso_de_uso", casos_uso_dir, "CU"),
            ):
                valor = fila.get(columna) or ""
                for id_encontrado in ID_HU_CU_RE.findall(valor):
                    if not id_encontrado.startswith(tipo + "-"):
                        continue  # ej. no validar un CU- encontrado dentro de la columna historia_usuario
                    if id_encontrado in IDS_LEGADO_CAJAS:
                        legado_path = historias_legado_path if tipo == "HU" else casos_uso_legado_path
                        if not id_existe_en_archivo_legado(legado_path, id_encontrado):
                            errores.append(
                                f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                                f"(ID legado de Cajas) pero no aparece en {os.path.relpath(legado_path)}"
                            )
                        continue
                    if not archivo_existe_con_prefijo(directorio, id_encontrado):
                        errores.append(
                            f"{req_id} (fila {numero_fila}): columna {columna} cita '{id_encontrado}' "
                            f"pero no existe ningun archivo '{id_encontrado}-*.md' en {os.path.relpath(directorio)}"
                        )

        # Validacion 3 (M23): IDs de requisito sincronizados entre la matriz y el SRS.
        ids_srs = ids_requisito_en_srs(srs_path)
        solo_en_matriz = sorted(ids_matriz - ids_srs)
        solo_en_srs = sorted(ids_srs - ids_matriz)
        for id_ in solo_en_matriz:
            errores.append(
                f"{id_}: existe en la matriz pero no tiene encabezado '#### {id_}' / '##### {id_}' en {os.path.relpath(srs_path)}"
            )
        for id_ in solo_en_srs:
            errores.append(
                f"{id_}: tiene encabezado en {os.path.relpath(srs_path)} pero no existe ninguna fila en la matriz"
            )

        if errores:
            print(f"Matriz de trazabilidad invalida: {len(errores)} problema(s) en {total_filas} filas.", file=sys.stderr)
            for err in errores:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)

        print(f"Matriz de trazabilidad valida: {total_filas} filas, 0 problemas.")


def es_vacio(valor):
    # Un campo se considera vacio si esta en blanco tras recortar espacios.
    # Una nota explicativa como "-- (decision arquitectonica, ver ADR-011)"
    # NO se considera vacio: es contenido real que documenta por que no hay
    # una historia de usuario tradicional, no una celda sin completar.
    return valor is None or valor.strip() == ""


if __name__ == "__main__":
    main()
PYEOF