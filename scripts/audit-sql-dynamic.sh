#!/usr/bin/env bash
# ============================================================================
# scripts/audit-sql-dynamic.sh
#
# Bloque A.2.3 de la guía de Entrega Final: rechaza la construcción de SQL
# dinámico dentro de los procedimientos/funciones versionados en
# db/procs/*.sql. Complementa al análisis estático de spotbugs+find-sec-bugs
# (pom.xml, regla SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE y variantes) --
# ese analiza el código Java del backend; este script analiza directamente
# el SQL fuente de los procedimientos, una capa que SpotBugs no toca.
#
# QUÉ RECHAZA (case-insensitive, alguien podría escribir en minúsculas):
#   1. `EXECUTE IMMEDIATE` -- sintaxis de PL/SQL (Oracle), no de PostgreSQL,
#      pero se detecta igual por si aparece código copiado/adaptado de otro
#      motor sin ajustar.
#   2. `sp_executesql` -- procedimiento de T-SQL (SQL Server) para ejecutar
#      SQL dinámico; mismo motivo que el punto anterior.
#   3. La palabra clave `EXECUTE` a secas (con límite de palabra, para no
#      confundirla con identificadores que la contienen). En PL/pgSQL (el
#      lenguaje real usado en db/procs/, ver comentario de cabecera de cada
#      archivo) la ÚNICA forma de ejecutar SQL construido dinámicamente
#      (por concatenación `||`, `format()`, `concat()`, etc.) es a través de
#      una sentencia `EXECUTE`. Los 9 archivos de db/procs/ son funciones
#      `LANGUAGE plpgsql` con SQL fijo y parámetros bindeados nombrados
#      (`p_...`) -- no tienen ningún motivo legítimo para usar `EXECUTE`.
#      Se prefiere esta regla (detectar la presencia de `EXECUTE` en sí)
#      sobre intentar además exigir que aparezca junto a `||` en la misma
#      sentencia: distinguir con regex una concatenación real de SQL
#      dinámico de un uso inocuo de `||` en otra parte del archivo (ver
#      db/procs/sp_anular_multa.sql, que concatena un prefijo de texto para
#      un valor de auditoría, no para SQL ejecutable) sería frágil; el
#      verdadero indicador inequívoco en PostgreSQL es la propia sentencia
#      EXECUTE, no el operador de concatenación por sí solo.
#
# EXIT CODES:
#   0 -- limpio, ningún patrón prohibido encontrado en db/procs/*.sql.
#   1 -- se encontró al menos una ocurrencia de alguno de los 3 patrones de
#        arriba; el mensaje impreso indica archivo, número de línea, y cuál
#        patrón coincidió, para cada ocurrencia encontrada (no se detiene en
#        la primera, reporta todas antes de salir con código 1).
#   2 -- error de uso/entorno (ej. db/procs/ no existe) -- no es un hallazgo
#        de SQL dinámico, es un problema para ejecutar el propio script.
# ============================================================================
set -uo pipefail

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
  echo "ERROR: no se encontró ningún archivo .sql en $PROCS_DIR" >&2
  exit 2
fi

violations_found=0

for f in "${sql_files[@]}"; do
  rel="${f#"$ROOT_DIR"/}"

  # 1) EXECUTE IMMEDIATE
  while IFS=: read -r line_num line_content; do
    echo "RECHAZADO: $rel:$line_num -- 'EXECUTE IMMEDIATE' (sintaxis de SQL dinámico, no permitido en db/procs/): $line_content"
    violations_found=1
  done < <(grep -inE '\bexecute[[:space:]]+immediate\b' "$f")

  # 2) sp_executesql
  while IFS=: read -r line_num line_content; do
    echo "RECHAZADO: $rel:$line_num -- 'sp_executesql' (SQL dinámico, no permitido en db/procs/): $line_content"
    violations_found=1
  done < <(grep -inE '\bsp_executesql\b' "$f")

  # 3) EXECUTE a secas (cualquier construcción de SQL dinámico en PL/pgSQL
  #    pasa por esta sentencia). Se excluye la propia línea si ya coincidió
  #    con "EXECUTE IMMEDIATE" arriba, para no reportarla dos veces.
  while IFS=: read -r line_num line_content; do
    if echo "$line_content" | grep -qiE '\bexecute[[:space:]]+immediate\b'; then
      continue
    fi
    echo "RECHAZADO: $rel:$line_num -- 'EXECUTE' (SQL dinámico en PL/pgSQL, no permitido en db/procs/): $line_content"
    violations_found=1
  done < <(grep -inE '\bexecute\b' "$f")
done

if [ "$violations_found" -eq 1 ]; then
  echo "" >&2
  echo "audit-sql-dynamic.sh: se encontraron construcciones de SQL dinámico prohibidas en db/procs/. Ver detalle arriba." >&2
  exit 1
fi

echo "audit-sql-dynamic.sh: OK -- ningún patrón de SQL dinámico prohibido en ${#sql_files[@]} archivo(s) de db/procs/."
exit 0
