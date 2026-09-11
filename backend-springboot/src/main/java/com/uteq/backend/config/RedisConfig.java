package com.uteq.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.interceptor.CacheErrorHandler;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    // Caches con serialización JDK estándar (PageImpl no deserializa en JSON).
    // "libros": paginado del catálogo. "sugerencias-libros": autocompletado con TTL corto.
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.libros.ttl-seconds}") long librosTtlSeconds,
            @Value("${app.cache.sugerencias.ttl-seconds}") long sugerenciasTtlSeconds) {
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues();

        // TTL externo (application.yml), nunca hardcodeado en Java.
        RedisCacheConfiguration librosConfig = baseConfig.entryTtl(Duration.ofSeconds(librosTtlSeconds));

        // TTL propio en segundos: el autocompletado se teclea letra por letra.
        RedisCacheConfiguration sugerenciasConfig = baseConfig.entryTtl(Duration.ofSeconds(sugerenciasTtlSeconds));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(Map.of(
                        "libros", librosConfig,
                        "sugerencias-libros", sugerenciasConfig))
                .transactionAware()
                .build();
    }

    @Bean
    /**
     * Executes the cacheErrorHandler operation.
     * @return operation result
     */
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            private final Logger log = LoggerFactory.getLogger(CacheErrorHandler.class);
            @Override public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache get error (Redis degradado) cache={} key={}: {}", cache.getName(), key, e.toString());
            }
            @Override public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Cache put error (Redis degradado) cache={} key={}: {}", cache.getName(), key, e.toString());
            }
            @Override public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache evict error (Redis degradado) cache={} key={}: {}", cache.getName(), key, e.toString());
            }
            @Override public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                log.warn("Cache clear error (Redis degradado) cache={}: {}", cache.getName(), e.toString());
            }
        };
    }

    @Bean
    /**
     * Executes the redisTemplate operation.
     * @param connectionFactory value required by the operation
     * @return operation result
     */
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
