# Evidencia — TTL del cache Redis "libros" en configuración externa

## Cabecera de medición

<!-- Retro-ajustada al formato de scripts/mediciones-header.sh (el script
no existía cuando se generó esta evidencia originalmente; los valores de
abajo son los reales de esa corrida, solo se homogeneizó el formato). -->
- **Fecha (ISO 8601 UTC)**: 2026-07-21T00:00:00Z aprox. (verificación
  inicial, sección 2) y 2026-07-22T01:29:45Z (repetición con cabeceras
  completas, sección 2.1 — timestamp real tomado del header `Date` de la
  respuesta; es el valor preciso, úsese este si se necesita un único dato)
- **Commit**: `cabf563` (`feat(cache): declara TTL del cache "libros" en
  configuracion externa` — este mismo archivo se agregó en ese commit)
- **Docker**: Docker version 29.5.3, build d1c06ef
- **Docker Compose**: Docker Compose version v5.1.4
- **Java**: openjdk version "21.0.11" (Eclipse Temurin)
- **Maven**: Apache Maven 3.9.12
- **PostgreSQL** (contenedor `sgb_postgres`): 16.14 (imagen `postgres:16-alpine`)
- **Redis** (contenedor `sgb_redis`): 7.4.9 (imagen `redis:7-alpine`)
- **curl**: 8.18.0 (libcurl/8.18.0) — herramienta adicional usada en esta
  evidencia, fuera del set fijo del script
- **Adicional**: Spring Boot 4.0.6, Spring Data Redis (spring-data-commons 4.0.5)

## Contexto

**Entorno**: mismo stack Docker Compose local de la evidencia de cookies
(`docs/mediciones/sec/2026-07-21-cookie-refresh-token.md`), tras
`docker compose up -d --build backend`.
**Propósito**: evidencia cruda para el requisito A.1 ("el cache Redis...
ahora con TTL declarado en configuración externa") y para
`docs/adr/adr-008-ttl-cache-libros.md`.

## Hallazgo durante la verificación (documentado también en ADR-008 y en RedisConfig)

Al verificar en vivo que "la segunda llamada debe venir de cache" se
descubrió que, tal como estaba configurado antes de esta sesión, el cache
`"libros"` **nunca servía una lectura real**: la escritura a Redis siempre
funcionaba, pero la lectura de vuelta lanzaba una excepción no controlada
(500) por incompatibilidad entre `GenericJackson2JsonRedisSerializer` y el
tipo `Page`/`PageImpl` de Spring Data (no tiene un constructor que Jackson
pueda usar para reconstruirlo). Se corrigió cambiando el cache `"libros"` a
serialización Java estándar (`Page`, `PageImpl`, `PageRequest` y `Sort` de
Spring Data ya son `Serializable`; se marcó `LibroResponseDTO` como
`Serializable` también). Detalle completo en los comentarios de
`RedisConfig.java`.

## 1. Estado antes de la corrección — TTL infinito confirmado

Comando (sobre una key de cache preexistente, de antes de externalizar el
TTL):
```
docker exec sgb_redis redis-cli TTL 'libros::Page request [number: 0, size 10, sort: titulo: ASC]'
```
Resultado: `-1` (sin expiración — Redis usa `-1` para "existe pero sin
TTL configurado"), confirmando que antes de este cambio
`RedisCacheConfiguration.defaultCacheConfig()` no tenía `entryTtl()` y las
entradas nunca expiraban por sí solas.

## 2. Dos llamadas consecutivas a GET /api/v1/libros (usuario LECTOR)

Secuencia: registrar un usuario de prueba (rol LECTOR por defecto),
login, y dos `GET /api/v1/libros` consecutivos con el mismo `accessToken`.

```
=== Llamada 1 (miss) ===
HTTPSTATUS:200 TIME:0.169557
=== Llamada 2 (deberia ser HIT) ===
HTTPSTATUS:200 TIME:0.030434
=== cuerpos iguales? ===
IDENTICOS
```

La segunda llamada es ~5.5x más rápida (30ms vs 170ms) y el cuerpo de la
respuesta es byte-a-byte idéntico al de la primera — confirma que la
segunda llamada se sirvió desde Redis, no desde una nueva consulta a
Postgres.

## 2.1. Repetición con cabeceras completas (timestamp verificable)

Misma secuencia (usuario LECTOR nuevo, key de cache limpiada antes),
capturando cabeceras y cuerpo por separado
(`curl -s -D headers.txt -o body.json -w "BODY_BYTES:%{size_download} TIME:%{time_total}s"`):

```
=== Llamada 1 (miss) ===
BODY_BYTES:2368 TIME:0.020099s
HTTP/1.1 200
...
Date: Wed, 22 Jul 2026 01:29:45 GMT

=== Llamada 2 (HIT esperado) ===
BODY_BYTES:2368 TIME:0.011993s
HTTP/1.1 200
...
Date: Wed, 22 Jul 2026 01:29:45 GMT

=== cuerpos identicos? ===
IDENTICOS
=== TTL ===
300
```

Mismo tamaño de body exacto (2368 bytes) en ambas llamadas, cuerpos
idénticos byte a byte, TTL recién fijado en 300 (igual al default de
`CACHE_LIBROS_TTL_SECONDS`). Confirma el mismo comportamiento que la
sección 2, con evidencia timestamped de forma verificable.

## 3. Key y TTL en Redis tras las dos llamadas

```
docker exec sgb_redis redis-cli KEYS '*'
```
```
libros::Page request [number: 0, size 10, sort: titulo: ASC]
```

```
docker exec sgb_redis redis-cli TTL 'libros::Page request [number: 0, size 10, sort: titulo: ASC]'
```
```
299
```

`299` segundos (~300s = 5 minutos), coincide con el valor por defecto de
`CACHE_LIBROS_TTL_SECONDS` en `.env.example` y `app.cache.libros.ttl-seconds`
en `application.yml` — confirma que el TTL activo viene de la configuración
externa, no de un valor hardcodeado en `RedisConfig.java`.

## 4. Nombre exacto de la key

`libros::Page request [number: 0, size 10, sort: titulo: ASC]` — prefijo
`libros::` (nombre del cache) seguido de la representación en texto que
Spring Cache genera por defecto a partir de los argumentos del método
(`Pageable`), al no definirse un `key = ...` explícito en `@Cacheable`. La
key varía si cambian los parámetros de paginación/orden de la llamada.
