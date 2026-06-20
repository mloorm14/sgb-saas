package com.uteq.backend;

import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration," +
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration," +
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
        "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration"
})
class BackendApplicationTests {

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private LibroRepository libroRepository;

    @MockitoBean
    private EditorialRepository editorialRepository;

    @MockitoBean
    private IdiomaRepository idiomaRepository;

    @MockitoBean
    private EstadoLibroRepository estadoLibroRepository;

    @Test
    void contextLoads() {
    }

}
