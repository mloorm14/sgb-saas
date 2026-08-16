#!/usr/bin/env bash
# ============================================================================
# scripts/audit-sql-dynamic.sh
#
# Auditoria estatica de SQL dinamico sobre db/procs/ (A.2.3 de la Tercera
# Entrega). Objetivo: demostrar de forma automatizada -- no con una revision
# manual puntual -- que ningun procedimiento almacenado del backend
# construye SQL por concatenacion de strings (inyeccion SQL). Complementa
# al analisis estatico de spotbugs+find-sec-bugs (pom.xml,
# spotbugs-security-include.xml) -- ese analiza el codigo Java del
# backend; este script analiza directamente el SQL fuente de los
# procedimientos, una capa que SpotBugs no toca.
#
# Que detecta, y por que ese patron y no otro:
#   1. La cadena literal `EXECUTE IMMEDIATE` (sintaxis Oracle/T-SQL, no
#      valida en PostgreSQL). Si aparece es senal de un copy-paste de otro
#      motor y de una query que se arma como string.
#   2. La cadena literal `sp_executesql` (sintaxis SQL Server): mismo caso.
#   3. Cualquier sentencia `EXECUTE` a secas, sin exigir ademas evidencia
#      de concatenacion (`||`/`format()`) en el propio argumento. Los 9
#      archivos de db/procs/ son funciones `LANGUAGE plpgsql` con SQL fijo
#      y parametros bindeados nombrados (`p_...`): ninguno tiene un motivo
#      legitimo para usar EXECUTE. Exigir prueba de concatenacion en la
#      misma linea seria mas preciso en el papel pero fragil en la
#      practica -- no cubre el caso donde el string dinamico se arma en
#      una variable y despues se ejecuta como `EXECUTE v_sql;` (el
#      argumento pasa a ser un identificador, no una concatenacion visible
#      en esa linea). El verdadero indicador inequivoco en PL/pgSQL es la
#      propia sentencia EXECUTE, no el operador de concatenacion por si
#      solo.
#
# Reglas de precision para no fabricar hallazgos que no son reales:
#   - Las lineas de comentario completo (empiezan en `--`) se ignoran en
#     los 3 checks: un comentario que menciona "EXECUTE" o "sp_executesql"
#     documenta el patron, no lo ejecuta. (Los comentarios inline -- despues
#     de codigo -- no se recortan: el `--` tambien es legal dentro de
#     literales de string, y recortar ciego podria ocultar un hallazgo
#     real. Hoy db/procs/ usa solo comentarios de linea completa.)
#   - Un "EXECUTE" precedido por comilla simple o doble, o pegado a un
#     identificador (ej. una columna/variable que contiene "execute" como
#     substring), no es una sentencia real -- se ignora.
#   - Los checks son independientes y conviven: un mismo statement puede
#     disparar mas de uno (p. ej. `EXECUTE IMMEDIATE 'x' || y` aparece en
#     el check 1 como literal y en el check 3 como sentencia EXECUTE).
#
# Uso: scripts/audit-sql-dynamic.sh  (desde la raiz del repo)
#
# EXIT CODES:
#   0 -- limpio, ningun patron prohibido encontrado en db/procs/*.sql.
#   1 -- se encontro al menos un hallazgo de SQL dinamico; el detalle
#        impreso indica archivo y numero de linea para cada ocurrencia
#        (no se detiene en la primera, reporta todas antes de salir).
#   2 -- error de uso/entorno (ej. db/procs/ no existe, o existe pero no
#        tiene ningun archivo .sql -- un audit vacio se leeria como un
#        falso "todo limpio", y eso es peor que fallar).
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROCS_DIR="$ROOT_DIR/db/procs"

if [ ! -d "$PROCS_DIR" ]; then
  echo "ERROR: no existe el directorio $PROCS_DIR" >&2
  exit 2
fi

shopt -s nullglob
sql_files=("$PROCS_DIR"/*.sql)
shopt -u nullglob

if [ ${#sql_files[@]} -eq 0 ]; then
  echo "ERROR: no se encontró ningún archivo .sql en $PROCS_DIR -- el audit estaría vacío" >&2
  echo "       y eso se leería como un falso 'todo limpio'. Revisar la ruta o el contenido." >&2
  exit 2
fi

findings=0

report_finding() {
    # $1 = mensaje ya formateado con archivo y numero de linea
    printf '%s\n' "$1"
    findings=$((findings + 1))
}

for f in "${sql_files[@]}"; do
    rel="${f#"$ROOT_DIR"/}"

    # Los 3 checks corren en un unico awk por archivo: NR conserva el
    # numero de linea del archivo real (un pipe con grep -v romperia ese
    # numero).
    while IFS= read -r finding; do
        report_finding "$finding"
    done < <(awk -v fname="$rel" '
        BEGIN { IGNORECASE = 1 }

        {
            # Linea de comentario completo: documentacion, no codigo ejecutable.
            if ($0 ~ /^[ \t]*--/) next

            # Check 1: literal EXECUTE IMMEDIATE (sintaxis Oracle/T-SQL)
            if ($0 ~ /(^|[^A-Za-z0-9_])(EXECUTE[ \t]+IMMEDIATE)/)
                printf "RECHAZADO: %s:%d: cadena literal '\''EXECUTE IMMEDIATE'\'' (sintaxis Oracle/T-SQL, no valida en PostgreSQL)\n", fname, NR

            # Check 2: literal sp_executesql (sintaxis SQL Server)
            if ($0 ~ /(^|[^A-Za-z0-9_])sp_executesql/)
                printf "RECHAZADO: %s:%d: cadena literal '\''sp_executesql'\'' (sintaxis SQL Server)\n", fname, NR

            # Check 3: cualquier sentencia EXECUTE a secas -- no exige
            # evidencia de || o format() en el argumento (ver cabecera del
            # script para el porque). Se ignoran ocurrencias pegadas a un
            # identificador o precedidas de comilla (texto dentro de un
            # literal de string, no una sentencia real).
            lc = $0
            n = length(lc)
            from = 1
            while (from <= n) {
                i = index(substr(lc, from), "execute")
                if (i == 0) break
                abs = from + i - 1
                from = abs + 1
                prev = (abs > 1) ? substr(lc, abs - 1, 1) : ""
                if (prev ~ /[A-Za-z0-9_]/) continue
                if (prev == sprintf("%c", 39) || prev == sprintf("%c", 34)) continue
                printf "RECHAZADO: %s:%d: sentencia EXECUTE (SQL dinamico en PL/pgSQL, no permitido en db/procs/)\n", fname, NR
            }
        }
    ' "$f")

done

if [ "$findings" -gt 0 ]; then
    echo "" >&2
    echo "audit-sql-dynamic.sh: se encontraron construcciones de SQL dinámico prohibidas en db/procs/. Ver detalle arriba." >&2
    exit 1
fi

echo "audit-sql-dynamic.sh: OK -- ningún patrón de SQL dinámico prohibido en ${#sql_files[@]} archivo(s) de db/procs/."
exit 0
