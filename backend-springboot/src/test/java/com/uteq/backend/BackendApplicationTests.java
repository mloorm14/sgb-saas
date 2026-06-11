package com.uteq.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Test
    void contextLoads() {
    }

}
