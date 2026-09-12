package com.uteq.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the public demo account contract on a fresh PostgreSQL database.
 *
 * Flyway applies the versioned seed/fix migrations against the container, so
 * this catches regressions where the demo account accidentally keeps GERENTE
 * or any other non-LECTOR role.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DemoAccountMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void flywayTest(DynamicPropertyRegistry registry) {
        java.net.URL classLocation = DemoAccountMigrationIntegrationTest.class
                .getProtectionDomain().getCodeSource().getLocation();
        java.nio.file.Path migrationsDir;
        try {
            migrationsDir = java.nio.file.Paths.get(classLocation.toURI())
                    .getParent()
                    .getParent()
                    .getParent()
                    .resolve("database/migrations");
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Could not resolve migrations path", e);
        }
        String migrationsPath = "filesystem:" + migrationsDir.toAbsolutePath();
        registry.add("spring.flyway.locations",
                () -> "classpath:db/test-migrations," + migrationsPath);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void demoAccount_afterMigrations_hasOnlyReaderRoleAndCanLogin() {
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT r.nombre
                  FROM usuarios u
                  JOIN usuario_roles ur ON ur.usuario_id = u.id
                  JOIN roles r ON r.id = ur.rol_id
                 WHERE u.correo = 'u@uteq.edu.ec'
                 ORDER BY r.nombre
                """, String.class);

        Boolean activeAndVerified = jdbcTemplate.queryForObject("""
                SELECT eu.nombre = 'ACTIVO' AND u.correo_verificado
                  FROM usuarios u
                  JOIN estados_usuario eu ON eu.id = u.estado_id
                 WHERE u.correo = 'u@uteq.edu.ec'
                """, Boolean.class);

        assertThat(roles).containsExactly("LECTOR");
        assertThat(activeAndVerified).isTrue();
    }
}
