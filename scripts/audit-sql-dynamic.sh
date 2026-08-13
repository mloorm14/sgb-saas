#!/usr/bin/env bash
# ============================================================================
# scripts/audit-sql-dynamic.sh
#
# Auditoria estatica de SQL dinamico sobre db/procs/ (A.2.3 de la Tercera
# Entrega). Objetivo: demostrar de forma automatizada -- no con una revision
# manual puntual -- que ningun procedimiento almacenado del backend
# construye SQL por concatenacion de strings (inyeccion SQL).
#
# Que detecta, y por que ese patron y no otro:
#   1. La cadena literal `EXECUTE IMMEDIATE` (sintaxis Oracle/T-SQL, no
#      valida en PostgreSQL). Si aparece es senal de un copy-paste de otro
#      motor y de una query que se arma como string.
#   2. La cadena literal `sp_executesql` (sintaxis SQL Server): mismo caso.
#   3. Concatenacion de strings (`||`) o `format(...)` como ARGUMENTO de un
#      `EXECUTE` dentro del archivo. Es el patron real de SQL dinamico en
#      PL/pgSQL: `EXECUTE 'SELECT * FROM ' || tabla;` o
#      `EXECUTE format('SELECT * FROM %I', tabla);`.
#
#      El operador `||` por si solo NO es un hallazgo: se usa tambien en
#      SQL legitimo (p. ej. concatenar texto para un mensaje de auditoria,
#      como en sp_anular_multa.sql), y no debe disparar el rechazo. Solo
#      importa cuando alimenta el argumento de un EXECUTE.
#
# Reglas de precision para no fabricar hallazgos que no son reales:
#   - Las lineas de comentario completo (empiezan en `--`) se ignoran en
#     los 3 checks: un comentario que menciona "EXECUTE" o "sp_executesql"
#     documenta el patron, no lo ejecuta. (Los comentarios inline -- despues
#     de codigo -- no se recortan: el `--` tambien es legal dentro de
#     literales de string, y recortar ciego podria ocultar un hallazgo
#     real. Hoy db/procs/ usa solo comentarios de linea completa.)
#   - Un "EXECUTE" precedido por comilla simple o doble es texto dentro de
#     un literal de string (p. ej. `SELECT 'execute ' || x`), no un
#     statement -- se ignora.
#   - Los 3 checks son independientes y conviven: un mismo statement puede
#     disparar mas de uno (p. ej. `EXECUTE IMMEDIATE 'x' || y` aparece en
#     check 1 como literal y en check 3 como argumento no constante).
#
# Limite documentado del heuristico: si el SQL dinamico se construye en una
# variable (`v_sql := 'SELECT ' || x; ... EXECUTE v_sql;`), el argumento
# del EXECUTE es un identificador y este grep no puede ver la concatenacion
# -- haria falta analisis de flujo de datos, fuera del alcance de
# bash/grep/awk. El repo no usa ese patron hoy; se documenta aca para que
# la revision manual de nuevos procs se enfoque solo en el.
#
# Uso: scripts/audit-sql-dynamic.sh  (desde la raiz del repo)
# Sale con codigo 0 si no hay hallazgos, 1 si hay al menos uno -- o si no
# hay archivos que auditar, porque un audit vacio seria un falso
# "todo limpio" (falso negativo) y eso es peor que fallar.
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROCS_DIR="$ROOT_DIR/db/procs"

if [ ! -d "$PROCS_DIR" ] || [ -z "$(ls -A "$PROCS_DIR"/*.sql 2>/dev/null)" ]; then
    echo "ERROR: falta $PROCS_DIR o no contiene archivos *.sql -- el audit estaria vacio" >&2
    echo "       y eso se leeria como un falso 'todo limpio'. Revisar la ruta o el contenido." >&2
    exit 1
fi

findings=0
files_reviewed=0

report_finding() {
    # $1 = mensaje ya formateado con archivo y numero de linea
    printf '%s\n' "$1"
    findings=$((findings + 1))
}

for sql_file in "$PROCS_DIR"/*.sql; do
    files_reviewed=$((files_reviewed + 1))

    # Los 3 checks corren en un unico awk por archivo: NR conserva el numero
    # de linea del archivo real (un pipe con grep -v romperia ese numero).
    while IFS= read -r finding; do
        report_finding "$finding"
    done < <(awk -v fname="$sql_file" '
        BEGIN { IGNORECASE = 1 }

        {
            # Linea de comentario completo: documentacion, no codigo ejecutable.
            if ($0 ~ /^[ \t]*--/) next

            # Check 1: literal EXECUTE IMMEDIATE (sintaxis Oracle/T-SQL)
            if ($0 ~ /(^|[^A-Za-z0-9_])(EXECUTE[ \t]+IMMEDIATE)/)
                printf "%s:%d: cadena literal '\''EXECUTE IMMEDIATE'\'' (sintaxis Oracle/T-SQL, no valida en PostgreSQL)\n", fname, NR

            # Check 2: literal sp_executesql (sintaxis SQL Server)
            if ($0 ~ /(^|[^A-Za-z0-9_])sp_executesql/)
                printf "%s:%d: cadena literal '\''sp_executesql'\'' (sintaxis SQL Server)\n", fname, NR

            # Check 3: argumento del EXECUTE construido por concatenacion.
            # Se toma el resto de la linea tras cada EXECUTE (con limite de
            # palabra) y se busca `||` o `format(` en el argumento. Si la
            # linea termina en `||` (concatenacion partida) o el EXECUTE
            # queda solo (argumento en la siguiente linea), se mira la
            # siguiente linea. Un argumento que es cadena literal fija o un
            # nombre de funcion sin interpolacion NO es un hallazgo.
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
                after = substr(lc, abs + 7)
                sub(/^[ \t]+/, "", after)
                if (after ~ /^immediate/) {
                    sub(/^immediate/, "", after)
                    sub(/^[ \t]+/, "", after)
                }
                if (after ~ /(\|\||format\()/) {
                    printf "%s:%d: EXECUTE con argumento no constante (|| o format()): %s\n", fname, NR, after
                } else if (after ~ /\|\|$/) {
                    printf "%s:%d: EXECUTE cuyo argumento termina en || (concatena en la siguiente linea): %s\n", fname, NR, after
                } else if (after == "") {
                    if ((getline nl) > 0) {
                        if (nl ~ /(\|\||format\()/) {
                            printf "%s:%d: EXECUTE con argumento no constante (|| o format()) en la linea %d: %s\n", fname, NR, NR + 1, nl
                        }
                    }
                }
            }
        }
    ' "$sql_file" || true)

done

if [ "$findings" -gt 0 ]; then
    echo
    echo "RESULTADO: $files_reviewed archivos revisados, $findings hallazgo(s) de SQL dinamico."
    echo "Cada hallazgo de arriba se reporta como ARCHIVO:LINEA. A.2.3 (y P1/P3 por cascada)"
    echo "quedan en 'Insuficiente' si hay concatenacion real hacia una query ejecutable."
    exit 1
fi

echo "RESULTADO: $files_reviewed archivos revisados, 0 hallazgos de SQL dinamico en db/procs/."
