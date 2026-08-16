package com.uteq.backend.integration;

import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.service.LibroService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración REAL contra Postgres (no mocks) de la portada binaria
 * del libro (V13__portada_imagen.sql, POST/GET
 * /api/v1/libros/{id}/portada). Misma premisa que
 * PrestamoMultaProcedureIntegrationTest: requiere el stack levantado
 * (application.yml por defecto apunta a localhost:5432/sgb_db) y
 * {@code @Transactional} en la clase para revertir cada test sin ensuciar
 * la base real.
 *
 * El objetivo acá es confirmar que el BYTEA se persiste y se recupera
 * byte a byte (no solo por tamaño) a través del service real, y que la
 * limpieza de portada_url al subir una portada también queda persistida.
 * El fixture se lee de src/test/resources/portada-fixture.png (PNG 1x1
 * válido de 70 bytes).
 */
@SpringBootTest
@Transactional
class LibroPortadaIntegrationTest {

    @Autowired LibroService libroService;
    @Autowired LibroRepository libroRepo;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EstadoLibroRepository estadoLibroRepo;

    // ── Test 1: subir portada persiste BYTEA byte a byte y limpia portada_url ──
    @Test
    void actualizarPortada_persisteBinarioByteAByteYLimpiarPortadaUrl() throws IOException {
        Long libroId = crearLibroConStock();
        // Simula el caso legacy: el libro ya tenía una URL externa que la
        // portada binaria reemplaza.
        jdbcTemplate.update(
                "UPDATE libros SET portada_url = ? WHERE id = ?",
                "https://host-externo/portada.png", libroId);
        byte[] esperados = leerFixture();
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "portada-fixture.png", "image/png", esperados);

        LibroResponseDTO resultado = libroService.actualizarPortada(libroId, archivo);

        assertThat(resultado.tienePortada()).isTrue();
        assertThat(resultado.portadaNombre()).isEqualTo("portada-fixture.png");
        assertThat(resultado.portadaTipo()).isEqualTo("image/png");

        // Flush explícito para que jdbcTemplate (misma transacción, canal
        // JDBC distinto del que usa Hibernate) vea lo persistido.
        libroRepo.flush();
        byte[] enBd = jdbcTemplate.queryForObject(
                "SELECT portada_imagen FROM libros WHERE id = ?", byte[].class, libroId);
        // Byte a byte, no solo por tamaño: es el requisito central de este
        // cambio (el binario viaja íntegro dentro de la BD).
        assertThat(enBd).isEqualTo(esperados);

        String portadaUrlEnBd = jdbcTemplate.queryForObject(
                "SELECT portada_url FROM libros WHERE id = ?", String.class, libroId);
        assertThat(portadaUrlEnBd).isNull();
    }

    // ── Test 2: la relectura por el service devuelve los mismos bytes ──
    @Test
    void obtenerPortada_trasSubir_retornaMismosBytesYTipo() throws IOException {
        Long libroId = crearLibroConStock();
        byte[] esperados = leerFixture();
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "portada-fixture.png", "image/png", esperados);
        libroService.actualizarPortada(libroId, archivo);

        PortadaImagenDTO portada = libroService.obtenerPortada(libroId);

        assertThat(portada.bytes()).isEqualTo(esperados);
        assertThat(portada.contentType()).isEqualTo("image/png");
    }

    // ── Test 3: libro sin portada -> 404 ──
    @Test
    void obtenerPortada_sinPortada_lanzaEntityNotFound() {
        Long libroId = crearLibroConStock();

        assertThatThrownBy(() -> libroService.obtenerPortada(libroId))
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

    private Long crearLibroConStock() {
        Integer estadoActivoId = estadoLibroRepo.findByNombre("ACTIVO")
                .map(EstadoLibro::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_libro sin fila 'ACTIVO' -- revisar db/seed.sql"));
        Integer editorialId = jdbcTemplate.queryForObject(
                "SELECT id FROM editoriales LIMIT 1", Integer.class);
        Integer idiomaId = jdbcTemplate.queryForObject(
                "SELECT id FROM idiomas LIMIT 1", Integer.class);
        String isbnUnico = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "INSERT INTO libros (isbn, titulo, anio_publicacion, editorial_id, idioma_id, " +
                        "estado_id, stock_total, stock_disponible) " +
                        "VALUES (?, 'Libro de prueba de integración', 2020, ?, ?, ?, 5, 5) " +
                        "RETURNING id",
                Long.class, isbnUnico, editorialId, idiomaId, estadoActivoId);
    }
}