#!/usr/bin/env bash
# ============================================================================
# scripts/mediciones-header.sh
#
# Genera a stdout el bloque de cabecera estándar que el Bloque B.2 de la
# guía exige en TODO archivo de resultados/evidencia: fecha ISO 8601 UTC,
# commit hash corto, y versiones exactas de las herramientas usadas. Antes
# de este script, esa cabecera se escribía a mano en cada archivo de
# docs/mediciones/sec/ — sistematizarla evita depender de que alguien se
# acuerde de repetir el formato cada vez.
#
# Uso: cualquier script de medición futuro (benchmarks, k6, análisis SUS,
# etc.) invoca esto AL PRINCIPIO de su ejecución y redirige la salida al
# archivo de resultados:
#
#     ./scripts/mediciones-header.sh >> docs/mediciones/perf/2026-08-01-carga-libros.md
#
# También puede ejecutarse solo (sin redirigir) para ver el bloque en pantalla.
#
# Ver docs/mediciones/TEMPLATE.md para la estructura completa esperada de un
# archivo de evidencia (esta cabecera es solo la primera sección) y
# docs/mediciones/README.md para la convención de nombres de archivo.
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Nombres de contenedor y credenciales tal como están fijados en
# docker-compose.yml/.env.example — no genérico, es específico de este
# proyecto (igual criterio que Makefile/scripts/build-init-sql.sh).
PG_CONTAINER="sgb_postgres"
PG_USER="sgb_user"
PG_DB="sgb_db"
REDIS_CONTAINER="sgb_redis"

# --- Helper: ejecuta un comando y cae a un texto de "no disponible" en vez
# de abortar todo el bloque si una sola herramienta falta o el contenedor
# correspondiente no está corriendo (mejor un dato faltante marcado
# explícitamente que un script que se cae a mitad de la cabecera). ---------
intentar() {
    local salida
    if salida="$("$@" 2>&1)"; then
        printf '%s' "$salida"
    else
        printf 'no disponible (comando falló: %s)' "$*"
    fi
}

FECHA_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if git -C "$ROOT_DIR" rev-parse --short HEAD >/dev/null 2>&1; then
    COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
else
    COMMIT="no disponible (no es un repositorio git o no hay commits)"
fi

DOCKER_VERSION="$(intentar docker --version)"
COMPOSE_VERSION="$(intentar docker compose version)"
JAVA_VERSION="$(java -version 2>&1 | head -1 || echo 'no disponible (java no encontrado)')"

if [ -x "$ROOT_DIR/backend-springboot/mvnw" ]; then
    MAVEN_VERSION="$("$ROOT_DIR/backend-springboot/mvnw" -v 2>&1 | head -1)"
else
    MAVEN_VERSION="no disponible (backend-springboot/mvnw no encontrado)"
fi

if docker inspect "$PG_CONTAINER" >/dev/null 2>&1; then
    POSTGRES_VERSION="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -tAc 'SHOW server_version;' 2>/dev/null | tr -d '[:space:]')"
    [ -z "$POSTGRES_VERSION" ] && POSTGRES_VERSION="no disponible (contenedor $PG_CONTAINER no respondió)"
else
    POSTGRES_VERSION="no disponible (contenedor $PG_CONTAINER no está corriendo)"
fi

if docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1; then
    REDIS_VERSION="$(docker exec "$REDIS_CONTAINER" redis-cli INFO server 2>/dev/null | grep '^redis_version:' | cut -d: -f2 | tr -d '[:space:]')"
    [ -z "$REDIS_VERSION" ] && REDIS_VERSION="no disponible (contenedor $REDIS_CONTAINER no respondió)"
else
    REDIS_VERSION="no disponible (contenedor $REDIS_CONTAINER no está corriendo)"
fi

cat <<EOF
## Cabecera de medición

- **Fecha (ISO 8601 UTC)**: ${FECHA_ISO}
- **Commit**: \`${COMMIT}\`
- **Docker**: ${DOCKER_VERSION}
- **Docker Compose**: ${COMPOSE_VERSION}
- **Java**: ${JAVA_VERSION}
- **Maven**: ${MAVEN_VERSION}
- **PostgreSQL** (contenedor \`${PG_CONTAINER}\`): ${POSTGRES_VERSION}
- **Redis** (contenedor \`${REDIS_CONTAINER}\`): ${REDIS_VERSION}
EOF
