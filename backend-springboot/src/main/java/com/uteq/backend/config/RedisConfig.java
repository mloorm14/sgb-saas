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

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    // HALLAZGO (detectado al verificar TAREA 2 en vivo): el cache "libros"
    // cachea Page<LibroResponseDTO> (LibroService.listar()). Se intentó con
    // GenericJackson2JsonRedisSerializer + "default typing" + el módulo
    // Jackson de Spring Data Web para Page, y aun así falló: PageImpl no
    // expone un constructor que Jackson pueda usar para reconstruirlo al
    // leer de vuelta (funciona para SERIALIZAR -- por eso la respuesta HTTP
    // de LibroController nunca tuvo problema -- pero no para deserializar).
    // En la práctica esto significa que, tal como estaba configurado antes,
    // el cache "libros" nunca sirvió una lectura real: la escritura siempre
    // funcionó, la lectura siempre lanzaba una excepción no controlada.
    // Solución: no usar JSON para los valores de este cache. Se deja el
    // serializador de valores SIN configurar explícitamente, con lo que
    // RedisCacheConfiguration.defaultCacheConfig() aplica su propio default
    // (serialización Java estándar, JdkSerializationRedisSerializer). Page,
    // PageImpl, PageRequest y Sort de Spring Data ya son java.io.Serializable
    // de fábrica; LibroResponseDTO se marcó Serializable explícitamente para
    // completar el grafo (ver LibroResponseDTO). Las keys siguen siendo
    // String vía el default de Spring Data Redis.
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.libros.ttl-seconds}") long librosTtlSeconds) {
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues();

        // TTL del cache "libros" (GET /api/v1/libros, ver LibroService) viene
        // de configuracion externa -- app.cache.libros.ttl-seconds en
        // application.yml, resuelto desde CACHE_LIBROS_TTL_SECONDS en .env --
        // nunca hardcodeado en Java (requisito A.1 de la guia). Antes de este
        // cambio RedisCacheConfiguration.defaultCacheConfig() no tenia
        // entryTtl(): las entradas no expiraban nunca (TTL infinito), solo se
        // invalidaban via @CacheEvict en las mutaciones de LibroService.
        RedisCacheConfiguration librosConfig = baseConfig.entryTtl(Duration.ofSeconds(librosTtlSeconds));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(Map.of("libros", librosConfig))
                .build();
    }

    @Bean
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
