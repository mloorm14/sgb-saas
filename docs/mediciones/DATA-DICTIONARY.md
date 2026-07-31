# Diccionario de datos — `docs/mediciones/`

Documenta los campos/variables que aparecen en la evidencia cruda ya
existente en `docs/mediciones/`, requisito E.3 de la guía de la Tercera
Entrega.

> **Alcance actual**: al momento de escribir este diccionario, el
> Bloque C (pruebas de carga k6, auditoría OWASP más allá de los 2
> hallazgos ya documentados, encuesta SUS, Lighthouse, cobertura JaCoCo)
> **no se ha iniciado** — solo existen los 2 archivos de
> `docs/mediciones/sec/` listados abajo. Este diccionario se ampliará con
> una sección nueva por cada tipo de medición en cuanto exista al menos
> un archivo real que la respalde (k6 en `docs/mediciones/perf/`, SUS,
> Lighthouse, JaCoCo) — no se documentan aquí campos de mediciones que
> todavía no existen.

## `docs/mediciones/sec/2026-07-21-cookie-refresh-token.md`

Evidencia de la migración del `refreshToken` a cookie HttpOnly (ver
`docs/adr/adr-007-cookies-jwt.md`).

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `Fecha (ISO 8601 UTC)` | timestamp | — | fecha/hora válida en formato `YYYY-MM-DDTHH:MM:SSZ` | Momento real en que se ejecutó la verificación, tomado del header `Date` de una respuesta HTTP real, no del reloj local sin verificar. |
| `Commit` | string (hash corto de git) | — | 7 caracteres hexadecimales | Commit exacto contra el que se corrió la verificación, para poder reproducirla exactamente sobre ese estado del código. |
| `Set-Cookie: refreshToken` | string (JWT compacto, 3 partes separadas por `.`) | — | JWT firmado HS256 válido | Token de refresco emitido por `/api/auth/login` o `/api/auth/refresh`, transportado como valor de la cookie. |
| `Path` (atributo de cookie) | string | — | siempre `/api/auth` en este proyecto | Restringe el envío automático de la cookie a las rutas de autenticación; el navegador no la adjunta en llamadas a `/api/v1/**`. |
| `Max-Age` (atributo de cookie) | entero | segundos | `604800` (7 días) en login/refresh; `0` en logout | Vida de la cookie; debe coincidir con `security.jwt.refresh-expiration-ms` del backend. `0` fuerza expiración inmediata (logout). |
| `Secure`, `HttpOnly`, `SameSite=Strict` (atributos de cookie) | booleano (presencia/ausencia) | — | los 3 presentes siempre | Los 3 atributos de seguridad exigidos por el requisito A.1; su ausencia sería un hallazgo de seguridad, no un valor "fuera de rango" tolerable. |
| `HTTPSTATUS` / código de estado HTTP | entero | — | `200` (login/refresh exitoso), `400` (refresh sin cookie), `204` (logout) | Código de respuesta observado para cada escenario probado (positivo y negativo). |
| `BODY_BYTES` | entero | bytes | `0` para `204 No Content`; > 0 para respuestas con cuerpo JSON | Tamaño del cuerpo de la respuesta; se usa para confirmar ausencia de fuga de datos (ej. que el `refreshToken` no aparece en el cuerpo de `/login`). |

## `docs/mediciones/sec/2026-07-21-cache-libros-ttl.md`

Evidencia del TTL externo del cache Redis `"libros"` (ver
`docs/adr/adr-008-ttl-cache-libros.md`).

| Campo / variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| `Fecha (ISO 8601 UTC)` | timestamp | — | fecha/hora válida | Igual que en la evidencia de cookies — momento real de la corrida. |
| `Commit` | string (hash corto de git) | — | 7 caracteres hexadecimales | Commit exacto verificado. |
| `TTL` (salida de `redis-cli TTL <key>`) | entero | segundos | `-1` (sin expiración, estado previo al fix); `0`–`300` (tras el fix, cuenta regresiva desde el default `CACHE_LIBROS_TTL_SECONDS`) | Segundos restantes antes de que Redis expire la entrada de cache automáticamente. `-1` es el valor especial de Redis para "existe pero sin TTL configurado". |
| Nombre de key de Redis (ej. `libros::Page request [...]` / `libros::SimpleKey []`) | string | — | prefijo `libros::` siempre | Key generada por Spring Cache a partir del nombre del cache (`"libros"`) y los argumentos del método `listar(Pageable)`; varía según los parámetros de paginación/orden de cada llamada. |
| `TIME` (tiempo de respuesta de `curl -w %{time_total}`) | decimal | segundos | ~0.15–0.20s en cache miss; ~0.01–0.03s en cache hit | Latencia observada de `GET /api/v1/libros`; la diferencia entre llamada 1 (miss) y llamada 2 (hit) es la evidencia de que el cache sirve la segunda lectura. |
| `BODY_BYTES` | entero | bytes | igual entre llamada 1 y 2 para la misma consulta | Confirma que el cuerpo de la respuesta cacheada es idéntico byte a byte al original, no una respuesta distinta o truncada. |
| `HTTPSTATUS` | entero | — | `200` en ambas llamadas | Código de respuesta esperado para una lectura exitosa del catálogo. |

## Referencias

- `docs/mediciones/README.md` (convención general de evidencia)
- `docs/mediciones/TEMPLATE.md` (plantilla de cada archivo de evidencia)
- `docs/adr/adr-007-cookies-jwt.md`, `docs/adr/adr-008-ttl-cache-libros.md`
