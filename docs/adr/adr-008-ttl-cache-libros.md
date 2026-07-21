# ADR-008: TTL del cache Redis del catálogo de libros en configuración externa

## Title

El TTL del cache Redis `"libros"` se declara en configuración externa
(`application.yml` / `.env`), no hardcodeado en Java.

## Context

La guía de la Tercera Entrega (A.1) exige que el cache Redis del endpoint
de listado ya seleccionado en la entrega anterior (`GET /api/v1/libros`,
`@Cacheable("libros")` en `LibroService.listar()`) "siga en el mismo
endpoint... ahora con TTL declarado en configuración externa (no en
código)".

**Nota de corrección respecto a la nota de sesiones anteriores**: no se
trata de mover un TTL hardcodeado — al revisar `RedisConfig` directamente
no existía ningún TTL configurado en absoluto.
`RedisCacheConfiguration.defaultCacheConfig()` no traía `.entryTtl(...)`,
así que las entradas del cache `"libros"` no expiraban nunca por sí solas
(TTL infinito); la única invalidación existente era manual, vía
`@CacheEvict(value = "libros", allEntries = true)` en `crear()`,
`actualizar()` y `eliminar()` de `LibroService`. Esto ya funcionaba
correctamente para mantener el cache consistente ante mutaciones conocidas
por el propio backend, pero no protegía contra el caso de que los datos
subyacentes cambiaran por otra vía (ej. una corrección manual directa en
Postgres) — de ahí que la guía pida además un TTL como capa de seguridad
adicional independiente de `@CacheEvict`.

Este ADR es nuevo (`ADR-008`) y no una edición de `ADR-003-jwt-redis.md`:
ADR-003 documenta el uso de Redis como blacklist de tokens JWT revocados,
una decisión arquitectónica distinta (autenticación, no cache de
catálogo) que comparte la misma instancia de Redis pero ningún otro punto
en común con esta decisión.

## Decision

Se agrega la propiedad `app.cache.libros.ttl-seconds` en
`backend-springboot/src/main/resources/application.yml`, resuelta desde la
variable de entorno `CACHE_LIBROS_TTL_SECONDS` (default `300` segundos = 5
minutos si no se define). `RedisConfig.cacheManager(...)` la inyecta vía
`@Value` y construye una `RedisCacheConfiguration` específica para el cache
`"libros"` con `.entryTtl(Duration.ofSeconds(librosTtlSeconds))`, registrada
con `RedisCacheManager.Builder#withInitialCacheConfigurations(Map.of("libros", librosConfig))`
— el resto de caches que pudieran agregarse a futuro siguen usando
`cacheDefaults(baseConfig)` (sin TTL propio) salvo que se les configure uno
explícito por el mismo mecanismo.

- **Nombre exacto de la key en Redis**: `libros::SimpleKey []` (prefijo por
  defecto de Spring Cache: `<nombreCache>::<key>`; como
  `listar(Pageable pageable)` no define un `@Cacheable(key = ...)`
  explícito, Spring genera la key a partir de los argumentos del método —
  con un único `Pageable` por defecto, la key resultante es `SimpleKey []`
  para la primera página sin parámetros adicionales, y varía según los
  argumentos reales de paginación en cada llamada).
- **TTL por defecto**: 300 segundos, confirmado en vivo con `TTL
  libros::SimpleKey []` desde `redis-cli` dentro del contenedor
  `sgb_redis` (ver `docs/mediciones/sec/` para la evidencia cruda).

## Alternativas consideradas

- **Dejarlo hardcodeado (o sin TTL, como estaba) y solo documentar el
  gap:** descartado, es exactamente lo que la guía pide corregir (A.1).
- **`@Value` directo en `LibroService` sobre el propio `@Cacheable`:**
  descartado — la anotación `@Cacheable` de Spring no acepta un TTL
  dinámico vía SpEL de forma nativa sin acoplarse al proveedor de cache; el
  TTL es una propiedad de infraestructura (Redis), no de la lógica de
  negocio del servicio, así que pertenece a `RedisConfig`.
- **`@ConfigurationProperties` con una clase dedicada
  (`CacheProperties`)** en vez de `@Value` directo: se consideró más
  apropiado para múltiples propiedades relacionadas, pero con una sola
  propiedad (`ttl-seconds`) `@Value` es más simple y igual de externo a
  Java/hardcodeo — se prefiere evitar la clase adicional mientras solo haya
  un valor.

## Status

Aceptado e implementado. Verificado en vivo contra el stack Docker
(`docker compose restart backend`, sin volumen limpio): dos llamadas
consecutivas a `GET /api/v1/libros` y confirmación de la key + TTL con
`redis-cli` dentro de `sgb_redis` (ver `docs/mediciones/sec/`).

## Consequences

**Positivas:**

- Cumple el requisito A.1 de la guía: el TTL es configuración externa
  (`CACHE_LIBROS_TTL_SECONDS`), ajustable por entorno sin recompilar.
- Cierra un gap real de higiene de cache que existía desde antes (TTL
  infinito, dependencia total de `@CacheEvict` manual).
- `withInitialCacheConfigurations` deja el mecanismo listo para que futuros
  caches (si se agregan) definan su propio TTL sin heredar el de `"libros"`
  por accidente.

**Negativas:**

- Un valor de TTL mal elegido en producción (muy alto) reintroduce el
  mismo riesgo de datos desactualizados que existía con el TTL infinito,
  aunque acotado; se eligió 300s como default conservador razonable para
  un catálogo de biblioteca (baja frecuencia de cambio real).
- Añade una variable de entorno más que mantener sincronizada entre
  `.env.example`, `application.yml` y `docker-compose.yml`.

## Referencias

- `backend-springboot/src/main/java/com/uteq/backend/config/RedisConfig.java`
- `backend-springboot/src/main/java/com/uteq/backend/service/LibroService.java`
- [[ADR-003-jwt-redis]] (mismo Redis, decisión de blacklist independiente)
- `docs/mediciones/sec/` (evidencia cruda de `redis-cli TTL`)
