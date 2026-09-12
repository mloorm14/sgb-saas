package com.uteq.backend.integration;

import com.uteq.backend.entity.StatusBook;
import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.repository.StatusBookRepository;
import com.uteq.backend.repository.StatusUserRepository;
import com.uteq.backend.repository.FineProcedureRepository;
import com.uteq.backend.repository.LoanProcedureRepository;
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
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Transactional
class LoanFineProcedureIntegrationTest {

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

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired LoanProcedureRepository loanProcRepo;
    @Autowired FineProcedureRepository fineProcRepo;
    @Autowired StatusUserRepository statusUserRepo;
    @Autowired StatusBookRepository statusBookRepo;

    // ── Test 1: devolución con atraso -> genera multa y bloquea al usuario ──
    @Test
    void registerLoanReturn_withAtraso_generaFineYBloqueaUser() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);

        forzarAtraso(loanId, 2);

        Map<String, Object> result = loanProcRepo.spRegisterLoanReturn(loanId);

        assertThat(result.get("o_prestamo_id")).isEqualTo(loanId);
        assertThat(result.get("o_hubo_multa")).isEqualTo(true);
        assertThat((BigDecimal) result.get("o_monto_multa"))
                .isGreaterThan(BigDecimal.ZERO);

        // El usuario debe quedar BLOQUEADO_POR_MULTA (efecto secundario del SP).
        Integer statusBlockedId = statusUserRepo.findByName("BLOQUEADO_POR_MULTA")
                .orElseThrow().getId();
        Integer statusUserCurrent = jdbcTemplate.queryForObject(
                "SELECT estado_id FROM usuarios WHERE id = ?", Integer.class, userId);
        assertThat(statusUserCurrent).isEqualTo(statusBlockedId);
    }

    // ── Test 2: devolución sin atraso -> no genera multa ──────
    @Test
    void registerLoanReturn_withoutAtraso_notGeneraFine() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);

        Map<String, Object> result = loanProcRepo.spRegisterLoanReturn(loanId);

        assertThat(result.get("o_hubo_multa")).isEqualTo(false);
        assertThat(result.get("o_monto_multa")).isNull();
    }

    // ── Test 3: doble devolución del mismo préstamo -> LB409 ──
    @Test
    void registerLoanReturn_dosVeces_lanzaExceptionWithSqlStateLB409() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);
        loanProcRepo.spRegisterLoanReturn(loanId); // primera devolución, OK

        assertThatThrownBy(() -> loanProcRepo.spRegisterLoanReturn(loanId))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(sqlState((Exception) ex)).isEqualTo("LB409"));
    }

    // ── Test 4: pagar multa la única pendiente -> desbloquea al usuario ──
    @Test
    void payFine_unicaPending_desbloqueaUser() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);
        forzarAtraso(loanId, 2);
        loanProcRepo.spRegisterLoanReturn(loanId); // genera la multa PENDIENTE

        Long fineId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, loanId);

        Map<String, Object> result = fineProcRepo.spPayFineProcedure(fineId);

        assertThat(result.get("o_multa_id")).isEqualTo(fineId);
        assertThat(result.get("o_usuario_desbloqueado")).isEqualTo(true);

        Integer statusActiveId = statusUserRepo.findByName("ACTIVO").orElseThrow().getId();
        Integer statusUserCurrent = jdbcTemplate.queryForObject(
                "SELECT estado_id FROM usuarios WHERE id = ?", Integer.class, userId);
        assertThat(statusUserCurrent).isEqualTo(statusActiveId);
    }

    // ── Test 5: anular multa con rol GERENTE -> desbloquea y audita ──
    @Test
    void voidFine_withRoleManager_desbloqueaUserYRegistraAudit() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);
        forzarAtraso(loanId, 2);
        loanProcRepo.spRegisterLoanReturn(loanId);

        Long fineId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, loanId);

        Map<String, Object> result = fineProcRepo.spVoidFineProcedure(
                fineId, "Motivo de prueba de integración", "GERENTE");

        assertThat(result.get("o_multa_id")).isEqualTo(fineId);
        assertThat(result.get("o_usuario_desbloqueado")).isEqualTo(true);

        // Verifica el efecto de auditoría documentado en sp_anular_multa.sql:
        // usuario_id queda NULL a propósito (limitación conocida del SP, no
        // recibe quién ejecuta, solo el rol), tabla_afectada='multas',
        // registro_id=multaId, detalles empieza con 'Multa anulada: '.
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT usuario_id, tabla_afectada, registro_id, detalles " +
                        "FROM bitacora_auditoria WHERE tabla_afectada = 'multas' AND registro_id = ? " +
                        "ORDER BY id DESC LIMIT 1",
                fineId);
        assertThat(audit.get("usuario_id")).isNull();
        assertThat(audit.get("registro_id")).isEqualTo(fineId);
        assertThat((String) audit.get("detalles")).startsWith("Multa anulada: ");
    }

    // ── Test 6: anular multa con rol inválido -> LB422 (defensa del propio SP) ──
    @Test
    void voidFine_withRoleInvalid_lanzaExceptionWithSqlStateLB422() {
        Long userId = createUserActive();
        Long bookId = createBookWithStock();
        Long loanId = loanProcRepo.spCreateLoanProcedure(userId, bookId, userId, 7);
        forzarAtraso(loanId, 2);
        loanProcRepo.spRegisterLoanReturn(loanId);

        Long fineId = jdbcTemplate.queryForObject(
                "SELECT id FROM multas WHERE prestamo_id = ?", Long.class, loanId);

        assertThatThrownBy(() -> fineProcRepo.spVoidFineProcedure(fineId, "motivo", "BIBLIOTECARIO"))
                .isInstanceOf(DataAccessException.class)
                .satisfies(ex -> assertThat(sqlState((Exception) ex)).isEqualTo("LB422"));
    }

    // ── Helpers de fixture ─────────────────────────────────────
    private Long createUserActive() {
        Integer statusActiveId = statusUserRepo.findByName("ACTIVO")
                .map(StatusUser::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_usuario sin fila 'ACTIVO' -- revisar db/seed.sql"));
        String emailUnico = "test-" + UUID.randomUUID() + "@correo.com";
        return jdbcTemplate.queryForObject(
                "INSERT INTO usuarios (nombre, apellido, correo, password_hash, estado_id, correo_verificado) " +
                        "VALUES ('Test', 'Integracion', ?, 'hash-no-relevante-para-este-test', ?, true) " +
                        "RETURNING id",
                Long.class, emailUnico, statusActiveId);
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

    // sp_crear_prestamo siempre fija fecha_devolucion_estimada = NOW() +
    // dias_prestamo (futuro) -- para forzar un escenario de atraso hay que
    // retroceder esa fecha manualmente después de crear el préstamo.
    private void forzarAtraso(Long loanId, int daysAtraso) {
        jdbcTemplate.update(
                "UPDATE prestamos SET fecha_devolucion_estimada = NOW() - (? || ' days')::INTERVAL WHERE id = ?",
                daysAtraso, loanId);
    }

    private String sqlState(Exception ex) {
        Throwable causa = ex;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }
        return (causa instanceof SQLException sqlEx) ? sqlEx.getSQLState() : null;
    }
}
