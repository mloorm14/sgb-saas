# Evidencia — TTL del cache Redis "libros" en configuración externa

**Fecha**: 2026-07-21
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
