#!/usr/bin/env bash
# ============================================================================
# scripts/owasp-audit.sh — Bloque C.2: re-verificación automatizada de los 4
# controles OWASP ya documentados manualmente en docs/mediciones/sec/ (A01,
# A03, A07, A09) contra el stack Docker real.
#
# Por qué existe: feature/notificaciones-y-verificacion (mergeada a main)
# agregó verificación de correo obligatoria -- un usuario recién registrado
# queda PENDIENTE_VERIFICACION y el login devuelve 403 hasta que se llame
# POST /api/auth/verificar-correo con el código correcto. Los 4 scripts
# originales asumían login inmediato tras el registro y ya no funcionan tal
# cual. Este script reproduce la MISMA metodología/casos de prueba de cada
# archivo original, insertando únicamente el paso de verificación donde
# antes había login directo (función registrar_y_verificar() más abajo) --
# no reinventa ningún caso de prueba.
#
# A02 (TLS) y A05 (CSP) quedan fuera de este script a propósito: A02
# depende de un despliegue público que todavía no existe, y A05
# corresponde a feature/seguridad-transporte (rama aún sin mergear) -- no
# se duplica ese trabajo aquí.
#
# Uso: ./scripts/owasp-audit.sh (o `make audit`)
# Genera: docs/mediciones/sec/YYYY-MM-DD-owasp-audit-automatizado.md
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="http://localhost:8080"
PG_CONTAINER="sgb_postgres"
PG_USER="sgb_user"
PG_DB="sgb_db"
REDIS_CONTAINER="sgb_redis"
PASSWORD="ClaveSegura123!"
# Sufijo único por corrida (no semilla fija: cada corrida real crea
# usuarios nuevos de verdad contra la base de desarrollo -- una semilla
# fija chocaría con "el correo ya está registrado" en la segunda corrida).
TS="$(date -u +%s)"

FECHA_ARCHIVO="$(date -u +%Y-%m-%d)"
OUT="docs/mediciones/sec/${FECHA_ARCHIVO}-owasp-audit-automatizado.md"

# ── a. Levantar el stack y esperar healthchecks (mismo patrón que 'make up') ──
echo "Levantando stack..."
docker compose up -d
echo "Esperando healthchecks..."
i=0
while docker compose ps | grep -qE "starting|unhealthy"; do
    i=$((i + 1))
    if [ "$i" -gt 30 ]; then
        echo "Timeout esperando healthchecks"
        exit 1
    fi
    sleep 2
done
echo "Todos los servicios estan arriba."

# ── c. Helper: registro + lectura de código en Redis + verificación + login ──
# Reemplaza el "registro directo -> login directo" que usaban los 4 scripts
# originales. Imprime dos líneas a stdout: id del usuario, luego accessToken
# (vacío si algo falla) -- el llamador las captura con mapfile.
registrar_y_verificar() {
    local nombre="$1" apellido="$2" correo="$3" password="$4"

    local reg
    reg="$(curl -s -X POST "$BASE_URL/api/auth/registro" -H "Content-Type: application/json" \
        -d "{\"nombre\":\"$nombre\",\"apellido\":\"$apellido\",\"correo\":\"$correo\",\"password\":\"$password\"}")"
    local id
    id="$(echo "$reg" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)"

    # Pequeño reintento: el SET en Redis ocurre dentro de la misma petición
    # de registro, pero se le da un margen por si la lectura inmediata gana
    # la carrera contra el commit de la transacción.
    local codigo=""
    local intento=0
    while [ -z "$codigo" ] && [ "$intento" -lt 5 ]; do
        codigo="$(docker exec "$REDIS_CONTAINER" redis-cli GET "verificacion-correo:$correo" 2>/dev/null | tr -d '\r\n')"
        [ -z "$codigo" ] && sleep 1
        intento=$((intento + 1))
    done

    if [ -z "$codigo" ]; then
        echo "$id"
        echo ""
        return 1
    fi

    curl -s -X POST "$BASE_URL/api/auth/verificar-correo" -H "Content-Type: application/json" \
        -d "{\"correo\":\"$correo\",\"codigo\":\"$codigo\"}" >/dev/null

    local login
    login="$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"correo\":\"$correo\",\"password\":\"$password\"}")"
    local token
    token="$(echo "$login" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)"

    echo "$id"
    echo "$token"
}

# ── b. Cabecera + sección de referencia ──
{
    echo "# Evidencia — Re-verificación automatizada OWASP A01/A03/A07/A09 (Bloque C.2)"
    echo ""
    "$SCRIPT_DIR/mediciones-header.sh"
    echo ""
    cat <<'EOF'
## Referencia — esto complementa, no reemplaza, la evidencia manual original

Este archivo es una **re-verificación automatizada** (`scripts/owasp-audit.sh`,
`make audit`) de 4 controles OWASP ya documentados manualmente en corridas
anteriores. Reproduce la MISMA metodología/casos de prueba de cada archivo
original citado abajo; el único cambio estructural es que
`feature/notificaciones-y-verificacion` (mergeada a main) ahora exige
verificar el correo antes de poder loguearse (`PENDIENTE_VERIFICACION` ->
bloqueo `403` hasta `POST /api/auth/verificar-correo`), así que el
"registro directo -> login directo" que usaban los scripts originales se
reemplaza aquí por `registrar_y_verificar()` (registro -> leer código de
Redis -> verificar-correo -> login) en cada punto donde antes había login
directo tras un registro.

**No reemplaza ni edita** los archivos originales:

- [`2026-07-30-owasp-a01-control-acceso-roto.md`](2026-07-30-owasp-a01-control-acceso-roto.md)
- [`2026-07-30-owasp-a03-inyeccion.md`](2026-07-30-owasp-a03-inyeccion.md)
- [`2026-07-30-owasp-a07-fix-rate-limiting-login.md`](2026-07-30-owasp-a07-fix-rate-limiting-login.md)
  (se reproduce la versión YA corregida -- rate limiting existe en el
  código actual; el gap original pre-fix queda en
  `2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md`)
- [`2026-07-30-owasp-a09-fix-logging-autenticacion.md`](2026-07-30-owasp-a09-fix-logging-autenticacion.md)
  (se reproduce la versión YA corregida -- el logging de autenticación
  existe en el código actual; el gap original pre-fix queda en
  `2026-07-30-owasp-a09-fallo-registro-monitoreo.md`)

**A02 (TLS) y A05 (CSP) quedan fuera de este script**: A02 depende de un
despliegue público que todavía no existe, y A05 corresponde a
`feature/seguridad-transporte` (rama aún sin mergear) -- no se duplica ese
trabajo aquí.

Usuarios de prueba de esta corrida: sufijo `.audit.TIMESTAMP` (distinto al
sufijo `.owasp` de las corridas manuales originales), para no chocar con
usuarios ya existentes en la base de desarrollo.
EOF
    echo ""
} >"$OUT"

# ============================================================================
# A01 — Control de acceso roto
# Reproduce: 2026-07-30-owasp-a01-control-acceso-roto.md
# Dos usuarios LECTOR; A intenta leer los préstamos de B (debe fallar con
# 403) y luego los suyos propios (control, debe funcionar con 200).
# ============================================================================
echo "=== A01 — Control de acceso roto ==="
CORREO_A="usuarioA.audit.${TS}@sgb-saas.local"
CORREO_B="usuarioB.audit.${TS}@sgb-saas.local"

mapfile -t RES_A < <(registrar_y_verificar "Ana" "Auditoria" "$CORREO_A" "$PASSWORD")
ID_A="${RES_A[0]:-}"
TOKEN_A="${RES_A[1]:-}"
mapfile -t RES_B < <(registrar_y_verificar "Beto" "Auditoria" "$CORREO_B" "$PASSWORD")
ID_B="${RES_B[0]:-}"

CROSS_STATUS=$(curl -s -o /tmp/owasp-audit-a01-cross.json -w "%{http_code}" -X GET "$BASE_URL/api/v1/prestamos/usuario/$ID_B" \
    -H "Authorization: Bearer $TOKEN_A")
OWN_STATUS=$(curl -s -o /tmp/owasp-audit-a01-own.json -w "%{http_code}" -X GET "$BASE_URL/api/v1/prestamos/usuario/$ID_A" \
    -H "Authorization: Bearer $TOKEN_A")

if [ "$CROSS_STATUS" = "403" ] && [ "$OWN_STATUS" = "200" ]; then
    RESULTADO_A01="PASA"
else
    RESULTADO_A01="FALLA"
fi

{
    echo "## A01 — Control de acceso roto"
    echo ""
    echo "Usuario A id=\`$ID_A\` ($CORREO_A), Usuario B id=\`$ID_B\` ($CORREO_B)."
    echo ""
    echo "**A lee los préstamos de B (\`GET /api/v1/prestamos/usuario/$ID_B\`)** — esperado \`403\`:"
    echo '```'
    echo "HTTP_STATUS:$CROSS_STATUS"
    cat /tmp/owasp-audit-a01-cross.json
    echo ""
    echo '```'
    echo ""
    echo "**A lee sus propios préstamos (\`GET /api/v1/prestamos/usuario/$ID_A\`, control)** — esperado \`200\`:"
    echo '```'
    echo "HTTP_STATUS:$OWN_STATUS"
    cat /tmp/owasp-audit-a01-own.json
    echo ""
    echo '```'
    echo ""
    echo "**Resultado: $RESULTADO_A01**"
    echo ""
} >>"$OUT"

# ============================================================================
# A03 — Inyección
# Reproduce: 2026-07-30-owasp-a03-inyeccion.md
# Caso 1: payload en 'correo' de login (rechazado por @Email, 400).
# Caso 2: payload en 'nombre'/'apellido' de registro (se guarda literal,
# 201) + verificación de integridad: login de un usuario YA verificado
# (usuario A de A01) sigue funcionando -- la tabla usuarios no se corrompió.
# ============================================================================
echo "=== A03 — Inyección ==="
CASO1_STATUS=$(curl -s -o /tmp/owasp-audit-a03-caso1.json -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"' OR '1'='1\",\"password\":\"cualquiera\"}")

CORREO_C="usuarioC.audit.${TS}@sgb-saas.local"
CASO2_STATUS=$(curl -s -o /tmp/owasp-audit-a03-caso2.json -w "%{http_code}" -X POST "$BASE_URL/api/auth/registro" -H "Content-Type: application/json" \
    -d "{\"nombre\":\"' OR '1'='1\",\"apellido\":\"'; DROP TABLE usuarios; --\",\"correo\":\"$CORREO_C\",\"password\":\"$PASSWORD\"}")

# Verificación de integridad: usuario A (ya verificado en el paso A01) sigue
# pudiendo loguearse -- si DROP TABLE hubiera corrido de verdad, esto fallaría.
INTEGRIDAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_A\",\"password\":\"$PASSWORD\"}")

if [ "$CASO1_STATUS" = "400" ] && [ "$CASO2_STATUS" = "201" ] && [ "$INTEGRIDAD_STATUS" = "200" ]; then
    RESULTADO_A03="PASA"
else
    RESULTADO_A03="FALLA"
fi

{
    echo "## A03 — Inyección"
    echo ""
    echo "**Caso 1 — payload en \`correo\` de login** — esperado \`400\` (rechazado por \`@Email\`, nunca llega a una consulta):"
    echo '```'
    echo "HTTP_STATUS:$CASO1_STATUS"
    cat /tmp/owasp-audit-a03-caso1.json
    echo ""
    echo '```'
    echo ""
    echo "**Caso 2 — payload en \`nombre\`/\`apellido\` de registro (incluye intento de \`DROP TABLE\`)** — esperado \`201\` (se guarda literal como texto):"
    echo '```'
    echo "HTTP_STATUS:$CASO2_STATUS"
    cat /tmp/owasp-audit-a03-caso2.json
    echo ""
    echo '```'
    echo ""
    echo "**Verificación de integridad** (login del usuario A, registrado y verificado antes del payload) — esperado \`200\`:"
    echo '```'
    echo "HTTP_STATUS:$INTEGRIDAD_STATUS"
    echo '```'
    echo ""
    echo "**Resultado: $RESULTADO_A03**"
    echo ""
} >>"$OUT"

# ============================================================================
# A07 — Fallos de identificación y autenticación (rate limiting de login)
# Reproduce: 2026-07-30-owasp-a07-fix-rate-limiting-login.md
# 6 intentos fallidos consecutivos (esperado: 401 x5, 429 en el sexto) +
# verificación de reseteo del contador tras un login exitoso.
# ============================================================================
echo "=== A07 — Rate limiting de login ==="
CORREO_D="usuarioA07.audit.${TS}@sgb-saas.local"
mapfile -t RES_D < <(registrar_y_verificar "Carla" "RateLimit" "$CORREO_D" "$PASSWORD")

CODES_D=()
for n in 1 2 3 4 5 6; do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
        -d "{\"correo\":\"$CORREO_D\",\"password\":\"ClaveIncorrecta$n\"}")
    CODES_D+=("$CODE")
done

CORREO_E="usuarioReseteo.audit.${TS}@sgb-saas.local"
mapfile -t RES_E < <(registrar_y_verificar "Elena" "Reseteo" "$CORREO_E" "$PASSWORD")

curl -s -o /dev/null -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_E\",\"password\":\"mala1\"}"
curl -s -o /dev/null -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_E\",\"password\":\"mala2\"}"

KEY_E="$(docker exec "$REDIS_CONTAINER" redis-cli KEYS "login-attempts:$CORREO_E:*" 2>/dev/null | tr -d '\r')"
CONTADOR_ANTES="$(docker exec "$REDIS_CONTAINER" redis-cli GET "$KEY_E" 2>/dev/null | tr -d '\r\n')"

LOGIN_EXITOSO_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_E\",\"password\":\"$PASSWORD\"}")
CONTADOR_DESPUES="$(docker exec "$REDIS_CONTAINER" redis-cli GET "$KEY_E" 2>/dev/null | tr -d '\r\n')"

if [ "${CODES_D[0]}" = "401" ] && [ "${CODES_D[1]}" = "401" ] && [ "${CODES_D[2]}" = "401" ] \
    && [ "${CODES_D[3]}" = "401" ] && [ "${CODES_D[4]}" = "401" ] && [ "${CODES_D[5]}" = "429" ] \
    && [ "$LOGIN_EXITOSO_STATUS" = "200" ] && [ "$CONTADOR_ANTES" = "2" ] && [ -z "$CONTADOR_DESPUES" ]; then
    RESULTADO_A07="PASA"
else
    RESULTADO_A07="FALLA"
fi

{
    echo "## A07 — Fallos de identificación y autenticación (rate limiting de login)"
    echo ""
    echo "**6 intentos fallidos consecutivos** contra \`$CORREO_D\` — esperado \`401\` en los primeros 5, \`429\` en el sexto:"
    echo '```'
    for n in 1 2 3 4 5 6; do
        echo "intento $n: ${CODES_D[$((n - 1))]}"
    done
    echo '```'
    echo ""
    echo "**Verificación de reseteo del contador** (usuario \`$CORREO_E\`): 2 fallos, luego login correcto."
    echo '```'
    echo "clave Redis: $KEY_E"
    echo "contador antes del login exitoso: $CONTADOR_ANTES"
    echo "HTTP_STATUS login exitoso: $LOGIN_EXITOSO_STATUS"
    echo "contador después del login exitoso: ${CONTADOR_DESPUES:-(vacío)}"
    echo '```'
    echo ""
    echo "**Resultado: $RESULTADO_A07**"
    echo ""
} >>"$OUT"

# ============================================================================
# A09 — Fallos de registro y monitoreo (logging de autenticación)
# Reproduce: 2026-07-30-owasp-a09-fix-logging-autenticacion.md
# 2 logins fallidos + 1 login exitoso + 1 logout; confirma que quedan
# registrados en logs de AuthService y en bitacora_auditoria.
# ============================================================================
echo "=== A09 — Logging de autenticación ==="
CORREO_F="usuarioA09.audit.${TS}@sgb-saas.local"
mapfile -t RES_F < <(registrar_y_verificar "Fernanda" "Logging" "$CORREO_F" "$PASSWORD")
ID_F="${RES_F[0]:-}"

# Marca de línea/id (no de tiempo -- evita depender de que el reloj del
# contenedor y el del host coincidan) justo después de que
# registrar_y_verificar() termine: el "Correo verificado" y el login interno
# del helper para obtener el token NO deben contarse como parte de la prueba
# real de este control (2 fallos + 1 éxito + 1 logout), solo lo que sigue.
LINEA_BASE="$(docker logs sgb_backend 2>&1 | wc -l)"
ID_BITACORA_BASE="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -tAc \
    "SELECT COALESCE(MAX(id), 0) FROM bitacora_auditoria;" 2>/dev/null | tr -d '\r\n')"
ID_BITACORA_BASE="${ID_BITACORA_BASE:-0}"

curl -s -o /dev/null -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_F\",\"password\":\"mala1\"}"
curl -s -o /dev/null -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_F\",\"password\":\"mala2\"}"

LOGIN_F="$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"correo\":\"$CORREO_F\",\"password\":\"$PASSWORD\"}")"
TOKEN_F="$(echo "$LOGIN_F" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)"

curl -s -o /dev/null -X POST "$BASE_URL/api/auth/logout" -H "Authorization: Bearer $TOKEN_F"

sleep 1
LOGS_F="$(docker logs sgb_backend 2>&1 | tail -n "+$((LINEA_BASE + 1))" | grep "AuthService" | grep "$CORREO_F")"
BITACORA_F="$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -tAc \
    "SELECT tipo_operacion FROM bitacora_auditoria WHERE detalles LIKE '%${CORREO_F}%' AND id > ${ID_BITACORA_BASE} ORDER BY id;" 2>/dev/null | tr -d '\r')"

NUM_LOGS_F="$(echo "$LOGS_F" | grep -c . || true)"
TIENE_LOGIN_OK="$(echo "$LOGS_F" | grep -c "Login exitoso: sub=$ID_F correo=$CORREO_F" || true)"
TIENE_LOGOUT_LOG="$(echo "$LOGS_F" | grep -c "Logout:" || true)"
TIENE_LOGIN_OK_BITACORA="$(echo "$BITACORA_F" | grep -c "LOGIN_OK" || true)"
TIENE_LOGOUT_BITACORA="$(echo "$BITACORA_F" | grep -c "LOGOUT" || true)"
TIENE_LOGIN_FAIL_BITACORA="$(echo "$BITACORA_F" | grep -c "LOGIN_FAIL" || true)"

if [ "$NUM_LOGS_F" -ge 4 ] && [ "$TIENE_LOGIN_OK" -ge 1 ] && [ "$TIENE_LOGOUT_LOG" -ge 1 ] \
    && [ "$TIENE_LOGIN_OK_BITACORA" -ge 1 ] && [ "$TIENE_LOGOUT_BITACORA" -ge 1 ] && [ "$TIENE_LOGIN_FAIL_BITACORA" -ge 2 ]; then
    RESULTADO_A09="PASA"
else
    RESULTADO_A09="FALLA"
fi

{
    echo "## A09 — Fallos de registro y monitoreo (logging de autenticación)"
    echo ""
    echo "Usuario \`$CORREO_F\` (id=\`$ID_F\`): 2 logins fallidos + 1 login exitoso + 1 logout."
    echo ""
    echo "**Logs de aplicación (\`docker logs sgb_backend\`, filtrados por AuthService + correo de esta corrida):**"
    echo '```'
    echo "$LOGS_F"
    echo '```'
    echo ""
    echo "**\`bitacora_auditoria\` (filtrada por este correo en \`detalles\`):**"
    echo '```'
    echo "$BITACORA_F"
    echo '```'
    echo ""
    echo "**Resultado: $RESULTADO_A09**"
    echo ""
} >>"$OUT"

rm -f /tmp/owasp-audit-a01-cross.json /tmp/owasp-audit-a01-own.json /tmp/owasp-audit-a03-caso1.json /tmp/owasp-audit-a03-caso2.json

# ── f. Resumen en pantalla ──
echo ""
echo "=========================================="
echo "Resumen — re-verificación OWASP automatizada"
echo "=========================================="
echo "A01 (control de acceso roto):            $RESULTADO_A01"
echo "A03 (inyección):                         $RESULTADO_A03"
echo "A07 (rate limiting de login):            $RESULTADO_A07"
echo "A09 (logging de autenticación):          $RESULTADO_A09"
echo "=========================================="
echo "Evidencia completa: $OUT"

if [ "$RESULTADO_A01" = "PASA" ] && [ "$RESULTADO_A03" = "PASA" ] && [ "$RESULTADO_A07" = "PASA" ] && [ "$RESULTADO_A09" = "PASA" ]; then
    exit 0
else
    exit 1
fi
