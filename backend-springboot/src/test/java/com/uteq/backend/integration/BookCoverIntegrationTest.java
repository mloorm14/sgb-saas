package com.uteq.backend.integration;

import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.entity.StatusBook;
import com.uteq.backend.repository.StatusBookRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.service.BookService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración REAL contra Postgres (no mocks) de la portada binaria
 * del libro (V13__portada_imagen.sql, POST/GET
 * /api/v1/libros/{id}/portada). Usa Testcontainers para levantar un
 * PostgreSQL 16 real y aislado por test class -- no requiere stack externo.
 * Flyway aplica las migraciones (incluyendo R__stored_procedures.sql y
 * V13__portada_imagen.sql) automaticamente contra el container.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class BookCoverIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // La función fn_auditoria_generica() solo vive en db/auditoria-triggers.sql
    // (fuera de Flyway): en BD fresca V40 fallaría sin ella. Se aporta vía
    // ubicación solo-test (src/test no se empaqueta: prod jamás la ve).
    @DynamicPropertySource
    static void flywayTest(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations",
                () -> "classpath:db/test-migrations,filesystem:../database/migrations");
    }

    @Autowired BookService bookService;
    @Autowired BookRepository bookRepo;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired StatusBookRepository statusBookRepo;

    // ── Test 1: subir portada persiste BYTEA byte a byte y limpia portada_url ──
    @Test
    void updateCover_persisteBinarioByteAByteYLimpiarCoverUrl() throws IOException {
        Long bookId = createBookWithStock();
        // Simula el caso legacy: el libro ya tenía una URL externa que la
        // portada binaria reemplaza.
        jdbcTemplate.update(
                "UPDATE libros SET portada_url = ? WHERE id = ?",
                "https://host-externo/portada.png", bookId);
        byte[] esperados = leerFixture();
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "portada-fixture.png", "image/png", esperados);

        BookResponseDTO result = bookService.updateCover(bookId, file);

        assertThat(result.tieneCover()).isTrue();
        assertThat(result.coverName()).isEqualTo("portada-fixture.png");
        assertThat(result.coverType()).isEqualTo("image/png");

        // Flush explícito para que jdbcTemplate (misma transacción, canal
        // JDBC distinto del que usa Hibernate) vea lo persistido.
        bookRepo.flush();
        byte[] Bd = jdbcTemplate.queryForObject(
                "SELECT portada_imagen FROM libros WHERE id = ?", byte[].class, bookId);
        // Byte a byte, no solo por tamaño: es el requisito central de este
        // cambio (el binario viaja íntegro dentro de la BD).
        assertThat(Bd).isEqualTo(esperados);

        String coverUrlBd = jdbcTemplate.queryForObject(
                "SELECT portada_url FROM libros WHERE id = ?", String.class, bookId);
        assertThat(coverUrlBd).isNull();
    }

    // ── Test 2: la relectura por el service devuelve los mismos bytes ──
    @Test
    void getCover_afterUpload_retornaMismosBytesYType() throws IOException {
        Long bookId = createBookWithStock();
        byte[] esperados = leerFixture();
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "portada-fixture.png", "image/png", esperados);
        bookService.updateCover(bookId, file);

        CoverImageDTO cover = bookService.getCover(bookId);

        assertThat(cover.bytes()).isEqualTo(esperados);
        assertThat(cover.contentType()).isEqualTo("image/png");
    }

    // ── Test 3: libro sin portada -> 404 ──
    @Test
    void getCover_withoutCover_lanzaEntityNotFound() {
        Long bookId = createBookWithStock();

        assertThatThrownBy(() -> bookService.getCover(bookId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("no tiene portada");
    }

    // ── Helpers de fixture ─────────────────────────────────────
    private byte[] leerFixture() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/portada-fixture.png")) {
            assertThat(in).as("fixture portada-fixture.png en classpath")
                    .isNotNull();
            return in.readAllBytes();
        }
    }

    private Long createBookWithStock() {
        Integer statusActiveId = statusBookRepo.findByName("ACTIVO")
                .map(StatusBook::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_libro sin fila 'ACTIVO' -- revisar db/seed.sql"));
        Integer publisherId = jdbcTemplate.queryForObject(
                "SELECT id FROM editoriales LIMIT 1", Integer.class);
        Integer languageId = jdbcTemplate.queryForObject(
                "SELECT id FROM idiomas LIMIT 1", Integer.class);
        String isbnUnico = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "INSERT INTO libros (isbn, titulo, anio_publicacion, editorial_id, idioma_id, " +
                        "estado_id, stock_total, stock_disponible) " +
                        "VALUES (?, 'Libro de prueba de integración', 2020, ?, ?, ?, 5, 5) " +
                        "RETURNING id",
                Long.class, isbnUnico, publisherId, languageId, statusActiveId);
    }
}