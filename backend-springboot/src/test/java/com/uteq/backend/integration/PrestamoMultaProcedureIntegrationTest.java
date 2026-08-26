package com.uteq.backend.integration;

import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.MultaProcedureRepository;
import com.uteq.backend.repository.PrestamoProcedureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de integración REAL contra Postgres (no mocks) para los 3
 * procedimientos multi-OUT que docs/basedatos/CATALOGO-SP.md marca como
 * "compilan pero nunca se ejecutaron en runtime": sp_registrar_devolucion,
 * sp_pagar_multa, sp_anular_multa.
 * <p>
 * Usa Testcontainers para levantar un PostgreSQL 16 real y aislado por
 * test class -- no requiere stack Docker Compose externo. Flyway aplica
 * las migraciones (incluyendo R__stored_procedures.sql con los 4 SP
 * multi-OUT) automaticamente contra el container.
 * <p>
 * @Transactional en la clase: cada @Test corre en su propia transacción,
 * revertida automáticamente al terminar -- no ensucia la base real entre
 * corridas. Los datos de prueba (usuario, libro) se crean vía JdbcTemplate
 * directo para tener control total sobre columnas NOT NULL que no expone
 * ningún service todavía (password_hash, editorial_id, idioma_id).
 * <p>
 * No hay infraestructura de test de integración previa en el proyecto
 * (verificado: pom.xml no tenia testcontainers). Este test usa Surefire
 * (mvnw test) porque no hay Failsafe configurado -- si el equipo quiere
 * separar unit tests de integración en fases de build distintas, hace
 * falta agregar maven-failsafe-plugin y renombrar esta clase a *IT, una
 * decisión de build que no tomé unilateralmente.
 */
@Testcontainers
@SpringBootTest
@Transactional
class PrestamoMultaProcedureIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PrestamoProcedureRepository prestamoProcRepo;
    @Autowired MultaProcedureRepository multaProcRepo;
    @Autowired EstadoUsuarioRepository estadoUsuarioRepo;
    @Autowired EstadoLibroRepository estadoLibroRepo;

    // ── Test 1: devolución con atraso -> genera multa y bloquea al usuario ──
    @Test
    void registrarDevolucion_conAtraso_generaMultaYBloqueaUsuario() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);

        forzarAtraso(prestamoId, 2);

        Map<String, Object> resultado = prestamoProcRepo.spRegistrarDevolucion(prestamoId);

        assertThat(resultado.get("o_prestamo_id")).isEqualTo(prestamoId);
        assertThat(resultado.get("o_hubo_multa")).isEqualTo(true);
        assertThat((BigDecimal) resultado.get("o_monto_multa"))
                .isGreaterThan(BigDecimal.ZERO);

        // El usuario debe quedar BLOQUEADO_POR_MULTA (efecto secundario del SP).
        Integer estadoBloqueadoId = estadoUsuarioRepo.findByNombre("BLOQUEADO_POR_MULTA")
                .orElseThrow().getId();
        Integer estadoUsuarioActual = jdbcTemplate.queryForObject(
                "SELECT estado_id FROM usuarios WHERE id = ?", Integer.class, usuarioId);
        assertThat(estadoUsuarioActual).isEqualTo(estadoBloqueadoId);
    }

    // ── Test 2: devolución sin atraso -> no genera multa ──────
    @Test
    void registrarDevolucion_sinAtraso_noGeneraMulta() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);

        Map<String, Object> resultado = prestamoProcRepo.spRegistrarDevolucion(prestamoId);

        assertThat(resultado.get("o_hubo_multa")).isEqualTo(false);
        assertThat(resultado.get("o_monto_multa")).isNull();
    }

    // ── Test 3: doble devolución del mismo préstamo -> LB409 ──
    @Test
    void registrarDevolucion_dosVeces_lanzaExcepcionConSqlStateLB409() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);
        prestamoProcRepo.spRegistrarDevolucion(prestamoId); // primera devolución, OK

        assertThatThrownBy(() -> prestamoProcRepo.spRegistrarDevolucion(prestamoId))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(sqlStateDe((Exception) ex)).isEqualTo("LB409"));
    }

    // ── Test 4: pagar multa la única pendiente -> desbloquea al usuario ──
    @Test
    void pagarMulta_unicaPendiente_desbloqueaUsuario() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);
        forzarAtraso(prestamoId, 2);
        prestamoProcRepo.spRegistrarDevolucion(prestamoId); // genera la multa PENDIENTE

        Long multaId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, prestamoId);

        Map<String, Object> resultado = multaProcRepo.spPagarMulta(multaId);

        assertThat(resultado.get("o_multa_id")).isEqualTo(multaId);
        assertThat(resultado.get("o_usuario_desbloqueado")).isEqualTo(true);

        Integer estadoActivoId = estadoUsuarioRepo.findByNombre("ACTIVO").orElseThrow().getId();
        Integer estadoUsuarioActual = jdbcTemplate.queryForObject(
                "SELECT estado_id FROM usuarios WHERE id = ?", Integer.class, usuarioId);
        assertThat(estadoUsuarioActual).isEqualTo(estadoActivoId);
    }

    // ── Test 5: anular multa con rol GERENTE -> desbloquea y audita ──
    @Test
    void anularMulta_conRolGerente_desbloqueaUsuarioYRegistraAuditoria() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);
        forzarAtraso(prestamoId, 2);
        prestamoProcRepo.spRegistrarDevolucion(prestamoId);

        Long multaId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, prestamoId);

        Map<String, Object> resultado = multaProcRepo.spAnularMulta(
                multaId, "Motivo de prueba de integración", "GERENTE");

        assertThat(resultado.get("o_multa_id")).isEqualTo(multaId);
        assertThat(resultado.get("o_usuario_desbloqueado")).isEqualTo(true);

        // Verifica el efecto de auditoría documentado en sp_anular_multa.sql:
        // usuario_id queda NULL a propósito (limitación conocida del SP, no
        // recibe quién ejecuta, solo el rol), tabla_afectada='multas',
        // registro_id=multaId, detalles empieza con 'Multa anulada: '.
        Map<String, Object> auditoria = jdbcTemplate.queryForMap(
                "SELECT usuario_id, tabla_afectada, registro_id, detalles " +
                        "FROM bitacora_auditoria WHERE tabla_afectada = 'multas' AND registro_id = ? " +
                        "ORDER BY id DESC LIMIT 1",
                multaId);
        assertThat(auditoria.get("usuario_id")).isNull();
        assertThat(auditoria.get("registro_id")).isEqualTo(multaId);
        assertThat((String) auditoria.get("detalles")).startsWith("Multa anulada: ");
    }

    // ── Test 6: anular multa con rol inválido -> LB422 (defensa del propio SP) ──
    @Test
    void anularMulta_conRolInvalido_lanzaExcepcionConSqlStateLB422() {
        Long usuarioId = crearUsuarioActivo();
        Long libroId = crearLibroConStock();
        Long prestamoId = prestamoProcRepo.spCrearPrestamo(usuarioId, libroId, usuarioId, 7);
        forzarAtraso(prestamoId, 2);
        prestamoProcRepo.spRegistrarDevolucion(prestamoId);

        Long multaId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, prestamoId);

        assertThatThrownBy(() -> multaProcRepo.spAnularMulta(multaId, "motivo", "BIBLIOTECARIO"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(sqlStateDe((Exception) ex)).isEqualTo("LB422"));
    }

    // ── Helpers de fixture ─────────────────────────────────────
    private Long crearUsuarioActivo() {
        Integer estadoActivoId = estadoUsuarioRepo.findByNombre("ACTIVO")
                .map(EstadoUsuario::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_usuario sin fila 'ACTIVO' -- revisar db/seed.sql"));
        String correoUnico = "test-" + UUID.randomUUID() + "@uteq.edu.ec";
        return jdbcTemplate.queryForObject(
                "INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado) " +
                        "VALUES ('Test', 'Integracion', ?, 'hash-no-relevante-para-este-test', ?, true) " +
                        "RETURNING id",
                Long.class, correoUnico, estadoActivoId);
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

    // sp_crear_prestamo siempre fija fecha_devolucion_estimada = NOW() +
    // dias_prestamo (futuro) -- para forzar un escenario de atraso hay que
    // retroceder esa fecha manualmente después de crear el préstamo.
    private void forzarAtraso(Long prestamoId, int diasAtraso) {
        jdbcTemplate.update(
                "UPDATE prestamos SET fecha_devolucion_estimada = NOW() - (? || ' days')::INTERVAL WHERE id = ?",
                diasAtraso, prestamoId);
    }

    private String sqlStateDe(Exception ex) {
        Throwable causa = ex;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }
        return (causa instanceof SQLException sqlEx) ? sqlEx.getSQLState() : null;
    }
}