# Decisión de diseño — Comportamiento ante caída de Redis (A07: Identification and Authentication Failures)

## Contexto

El sistema usa Redis para dos funciones dentro del flujo de login:

1. **Rate limiting** de intentos de login (protección contra fuerza bruta).
2. **Verificación de JWT revocados** (blacklist de tokens invalidados por logout, cambio de contraseña, bloqueo de cuenta, etc.).

Ante una caída de Redis, había dos opciones extremas:

- **Fail-open total**: el login sigue funcionando sin rate-limiting ni chequeo de revocación.
- **Fail-closed total**: el login se cae por completo si Redis no responde.

Se evaluó el impacto de cada componente por separado y se optó por un comportamiento **mixto**, no simétrico.

## Decisión

| Componente | Comportamiento ante caída de Redis | Justificación |
|---|---|---|
| Rate limiting | **Fail-open** | Si Redis cae, el rate-limiting se desactiva temporalmente pero el login sigue operativo. Hacer fail-closed acá convertiría cualquier caída de Redis en un DoS total del sistema de autenticación, dándole a un atacante una forma barata de tumbar el login completo tumbando solo Redis. Se prioriza disponibilidad, con el riesgo aceptado de una ventana sin protección contra fuerza bruta mientras dure el outage. |
| Verificación de JWT revocados | **Fail-closed** | Si Redis cae, los tokens se tratan como **no verificables** y el login/las requests autenticadas se rechazan. Este chequeo no es una medida de disponibilidad sino de **control de acceso**: si un token fue revocado (logout, cuenta comprometida, bloqueo administrativo) y el chequeo se abre, ese token vuelve a ser válido durante todo el outage. Ese riesgo se considera inaceptable, por lo que se prioriza seguridad sobre disponibilidad en este componente específico. |

## Justificación general

No se trata de "bajar la seguridad para no caernos", sino de una decisión consciente por componente:

- Donde el riesgo de fail-open es una ventana de menor protección ante fuerza bruta (rate limiting) → se prioriza disponibilidad.
- Donde el riesgo de fail-open es que un acceso ya revocado vuelva a ser válido (JWT revocados) → se prioriza control de acceso.

## Acción tomada

- El comportamiento de rate-limiting se mantiene sin cambios (fail-open).
- El chequeo de JWT revocados fue modificado para comportarse en **fail-closed** ante caída de Redis: la consulta falla con `DataAccessException` (p.ej. `DataAccessResourceFailureException`/`UncategorizedDataAccessException`, mismo patrón de excepciones del incidente 2026-08-14), el filtro responde **401** (ProblemDetail, no 401/500 genérico) y **no continúa la cadena de filtros**. Se eligió 401 sobre 503 porque es un chequeo de control de acceso: semánticamente comunica "no autenticado — no se pudo confirmar que el token siga siendo válido", consistente con el resto del filtro (tokens inválidos), y evita reusar el 503 que existe para endpoints públicos dependientes de Redis. El evento queda loggeado (bitácora best-effort, no depende de Redis) para diferenciar en monitoreo rechazos por outage de rechazos por revocación real.
- Se recomienda, como mejora futura, agregar alertas de monitoreo activo cuando Redis no responde, para reducir el tiempo de exposición en ambos escenarios.

## Referencia OWASP

Esta decisión se documenta como parte de la evidencia de **A07:2021 – Identification and Authentication Failures**, para dejar constancia de que el comportamiento ante fallas de Redis fue evaluado y decidido de forma deliberada, y no es un descuido de implementación.