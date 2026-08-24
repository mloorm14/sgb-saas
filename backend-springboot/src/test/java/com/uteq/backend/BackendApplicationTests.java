package com.uteq.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifica que el contexto de Spring carga correctamente.
 *
 * Usa perfil "test" con H2 en memoria (application-test.yml) para que
 * DataJpaRepositoriesAutoConfiguration cree todos los JpaRepository
 * automáticamente, eliminando la necesidad de agregar un @MockitoBean
 * por cada repositorio nuevo.
 *
 * Redis se excluye porque no hay servidor Redis en el entorno de test;
 * solo se mockea RedisConnectionFactory.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
                "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration," +
                "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration," +
                "org.springframework.boot.data.redis.autoconfigure.health.DataRedisReactiveHealthContributorAutoConfiguration"
})
@ActiveProfiles("test")
class BackendApplicationTests {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void contextLoads() {
    }

}
