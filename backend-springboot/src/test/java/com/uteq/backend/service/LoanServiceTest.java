package com.uteq.backend.service;

import com.uteq.backend.dto.LoanReturnResponseDTO;
import com.uteq.backend.dto.LoanRequestDTO;
import com.uteq.backend.dto.LoanResponseDTO;
import com.uteq.backend.dto.RenewalResponseDTO;
import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.entity.StatusLoan;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.LoanProcedureRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.repository.projection.ReportDelinquencyProjection;
import com.uteq.backend.repository.projection.ReportUsageByPeriodProjection;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock LoanRepository loanRepo;
    @Mock LoanProcedureRepository loanProcRepo;
    @Mock UserRepository userRepo;
    @Mock StatusLoanRepository statusLoanRepo;
    @Mock ReservationRepository reservationRepo;
    @Mock StatusReservationRepository statusReservationRepo;
    @Mock ConfigurationSystemService configurationSystemService;
    @Mock CredentialQrService credentialQrService;
    @Mock NotificationService notificationService;

    @InjectMocks LoanService loanService;

    // ── Test 1: creación exitosa (usuarioId directo) ───────
    @Test
    void create_withDataValids_invocaProcedimientoYRetornaDTO() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(loanProcRepo.spCreateLoanProcedure(1L, 2L, 5L, 7)).willReturn(99L);
        given(loanRepo.findById(99L)).willReturn(Optional.of(loanWithId(99L)));

        LoanResponseDTO result = loanService.create(
                new LoanRequestDTO(1L, null, 2L, 7, null), auth);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.userId()).isEqualTo(1L);
    }

    // ── Test 2: devolución sin atraso (sin multa) ─────────
    @Test
    void registerLoanReturn_withoutAtraso_notGeneraFine() {
        // Map.of(...) NO admite valores null -- se usa HashMap porque
        // o_monto_multa es null cuando no hubo atraso.
        Map<String, Object> mapaWithoutFine = new HashMap<>();
        mapaWithoutFine.put("o_prestamo_id", 10L);
        mapaWithoutFine.put("o_hubo_multa", false);
        mapaWithoutFine.put("o_monto_multa", null);
        given(loanProcRepo.spRegisterLoanReturn(10L)).willReturn(mapaWithoutFine);

        LoanReturnResponseDTO result = loanService.registerLoanReturn(10L);

        assertThat(result.loanId()).isEqualTo(10L);
        assertThat(result.huboFine()).isFalse();
        assertThat(result.amountFine()).isNull();
    }

    // ── Test 3: devolución con atraso (hubaMulta = true) ──
    @Test
    void registerLoanReturn_withAtraso_generaFine() {
        given(loanProcRepo.spRegisterLoanReturn(11L)).willReturn(Map.of(
                "o_prestamo_id", 11L,
                "o_hubo_multa", true,
                "o_monto_multa", new BigDecimal("2.50")
        ));
        given(loanRepo.findById(11L)).willReturn(Optional.of(loanWithId(11L)));

        LoanReturnResponseDTO result = loanService.registerLoanReturn(11L);

        assertThat(result.huboFine()).isTrue();
        assertThat(result.amountFine()).isEqualTo(new BigDecimal("2.50"));
        // Módulo 2: el dueño real del préstamo (usuarioId=1L, ver
        // prestamoConId) es a quien se le debe notificar, no un id
        // cualquiera.
        verify(notificationService).notifyFine(1L, 11L, new BigDecimal("2.50"));
    }

    // Sin atraso no hay multa que notificar -- no debe ni consultarse el
    // préstamo para esto.
    @Test
    void registerLoanReturn_withoutAtraso_notNotificaFine() {
        Map<String, Object> mapaWithoutFine = new HashMap<>();
        mapaWithoutFine.put("o_prestamo_id", 10L);
        mapaWithoutFine.put("o_hubo_multa", false);
        mapaWithoutFine.put("o_monto_multa", null);
        given(loanProcRepo.spRegisterLoanReturn(10L)).willReturn(mapaWithoutFine);

        loanService.registerLoanReturn(10L);

        verify(notificationService, org.mockito.Mockito.never())
                .notifyFine(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ── Test 4: acceso denegado cuando un LECTOR pide el id de otro usuario ──
    @Test
    void listByUser_cuandoReaderPideOtroUser_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));

        assertThatThrownBy(() -> loanService.listByUser(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 5: reporte aplica default de limite=10 (regresión del fix LIMIT NULL) ──
    @Test
    void reportBooksMostLoaned_withoutLimit_aplicaDefaultDiez() {
        given(loanProcRepo.fnReportBooksMostLoaned(10, null, null))
                .willReturn(List.of());

        loanService.reportBooksMostLoaned(null, null, null);

        // Verifica el fix del bug de LIMIT NULL: al no mandar limite,
        // el service debe pasar 10 (no null) al repositorio.
        verify(loanProcRepo).fnReportBooksMostLoaned(10, null, null);
    }

    // ── Test 6: renovación exitosa ─────────────────────────
    @Test
    void renew_loanVigenteWithoutRenewalsPrevias_extiendeDate() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Loan loan = loanWithId(50L);
        loan.setRenewalsRealizadas((short) 0);
        loan.setStatusLoanId(1);
        given(loanRepo.findById(50L)).willReturn(Optional.of(loan));
        given(statusLoanRepo.findById(1)).willReturn(Optional.of(statusLoan(1, "ACTIVO")));
        given(configurationSystemService.getValueEntero("max_renovaciones_default")).willReturn(2);
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusReservation(1, "PENDIENTE")));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(statusReservation(2, "LISTA_PARA_RETIRO")));
        given(reservationRepo.existsByBookIdAndStatusReservationIdInAndUserIdNot(2L, List.of(1, 2), 1L))
                .willReturn(false);
        given(configurationSystemService.getValueEntero("dias_prestamo_default")).willReturn(15);
        given(statusLoanRepo.findByName("RENOVADO")).willReturn(Optional.of(statusLoan(2, "RENOVADO")));

        RenewalResponseDTO result = loanService.renew(50L, auth);

        assertThat(result.renewalsRealizadas()).isEqualTo((short) 1);
        assertThat(result.renewalsRestantes()).isEqualTo((short) 1);
        assertThat(loan.getStatusLoanId()).isEqualTo(2);
        verify(loanRepo).save(loan);
    }

    // ── Test 7: préstamo vencido ────────────────────────────
    @Test
    void renew_loanOverdue_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Loan loan = loanWithId(51L);
        loan.setStatusLoanId(1);
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().minusDays(2));
        given(loanRepo.findById(51L)).willReturn(Optional.of(loan));
        given(statusLoanRepo.findById(1)).willReturn(Optional.of(statusLoan(1, "ACTIVO")));

        assertThatThrownBy(() -> loanService.renew(51L, auth))
                .isInstanceOf(LoanOverdueException.class);
    }

    // ── Test 8: límite de renovaciones alcanzado ───────────
    @Test
    void renew_limitRenewalsAlcanzado_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Loan loan = loanWithId(52L);
        loan.setStatusLoanId(1);
        loan.setRenewalsRealizadas((short) 2);
        given(loanRepo.findById(52L)).willReturn(Optional.of(loan));
        given(statusLoanRepo.findById(1)).willReturn(Optional.of(statusLoan(1, "ACTIVO")));
        given(configurationSystemService.getValueEntero("max_renovaciones_default")).willReturn(2);

        assertThatThrownBy(() -> loanService.renew(52L, auth))
                .isInstanceOf(LimitRenewalsExceededException.class);
    }

    // ── Test 9: material reservado por otro usuario ────────
    @Test
    void renew_materialReservadoByOtroUser_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Loan loan = loanWithId(53L);
        loan.setStatusLoanId(1);
        given(loanRepo.findById(53L)).willReturn(Optional.of(loan));
        given(statusLoanRepo.findById(1)).willReturn(Optional.of(statusLoan(1, "ACTIVO")));
        given(configurationSystemService.getValueEntero("max_renovaciones_default")).willReturn(2);
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusReservation(1, "PENDIENTE")));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(statusReservation(2, "LISTA_PARA_RETIRO")));
        given(reservationRepo.existsByBookIdAndStatusReservationIdInAndUserIdNot(2L, List.of(1, 2), 1L))
                .willReturn(true);

        assertThatThrownBy(() -> loanService.renew(53L, auth))
                .isInstanceOf(MaterialReservadoException.class);
    }

    // ── Test 10: LECTOR intenta renovar un préstamo ajeno ──
    @Test
    void renew_readerIntentaRenewLoanAjeno_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        Loan loan = loanWithId(54L); // usuarioId = 1L (ver helper)
        given(loanRepo.findById(54L)).willReturn(Optional.of(loan));
        given(userRepo.findByEmail("lector@correo.com")).willReturn(Optional.of(userWithId(99L)));

        assertThatThrownBy(() -> loanService.renew(54L, auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 11: préstamo ya devuelto ──────────────────────
    @Test
    void renew_loanYaDevuelto_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Loan loan = loanWithId(55L);
        loan.setStatusLoanId(3);
        given(loanRepo.findById(55L)).willReturn(Optional.of(loan));
        given(statusLoanRepo.findById(3)).willReturn(Optional.of(statusLoan(3, "DEVUELTO")));

        assertThatThrownBy(() -> loanService.renew(55L, auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 12: creación con credencial QR válida ──────────
    @Test
    void create_withCredentialQrValida_resuelveUserCorrecto() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        UUID token = UUID.randomUUID();
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(credentialQrService.resolveByToken(token)).willReturn(userWithId(3L));
        given(loanProcRepo.spCreateLoanProcedure(3L, 2L, 5L, 7)).willReturn(100L);
        given(loanRepo.findById(100L)).willReturn(Optional.of(loanWithId(100L)));

        LoanResponseDTO result = loanService.create(
                new LoanRequestDTO(null, token, 2L, 7, null), auth);

        assertThat(result.id()).isEqualTo(100L);
        verify(loanProcRepo).spCreateLoanProcedure(3L, 2L, 5L, 7);
    }

    // ── Test 13: credencial QR que no resuelve a ningún usuario ──
    @Test
    void create_withCredentialQrInexistente_lanza404() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        UUID token = UUID.randomUUID();
        given(credentialQrService.resolveByToken(token))
                .willThrow(new EntityNotFoundException("Credencial QR no reconocida o usuario inactivo."));

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(null, token, 2L, 7, null), auth))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 14: usuarioId y credencialQrToken ambos presentes ──
    @Test
    void create_withUserIdYCredentialQrAmbosPresentes_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(1L, UUID.randomUUID(), 2L, 7, null), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 15: ni usuarioId ni credencialQrToken ───────────
    @Test
    void create_withoutUserIdNiCredentialQr_lanzaException() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(null, null, 2L, 7, null), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 15b: préstamo nacido de una reserva vigente ─────
    // Ventanilla: el préstamo queda vinculado a la reservación
    // (prestamos.reservacion_id) y la reserva se marca RETIRADA para que no
    // quede pendiente tras la entrega.
    @Test
    void create_withReservationVigente_vinculaLoanYMarkRetirada() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Reservation reservation = reservationVigente(77L, 1L, 2L, 1); // PENDIENTE
        Loan loanCreated = loanWithId(99L);
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(reservationRepo.findById(77L)).willReturn(Optional.of(reservation));
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusReservation(1, "PENDIENTE")));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(statusReservation(2, "LISTA_PARA_RETIRO")));
        given(statusReservationRepo.findByName("RETIRADA"))
                .willReturn(Optional.of(statusReservation(3, "RETIRADA")));
        given(loanProcRepo.spCreateLoanProcedure(1L, 2L, 5L, 7)).willReturn(99L);
        given(loanRepo.findById(99L)).willReturn(Optional.of(loanCreated));

        LoanResponseDTO result = loanService.create(
                new LoanRequestDTO(1L, null, 2L, 7, 77L), auth);

        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.reservationId()).isEqualTo(77L);
        assertThat(loanCreated.getReservationId()).isEqualTo(77L);
        assertThat(reservation.getStatusReservationId()).isEqualTo(3);
        verify(loanRepo).save(loanCreated);
        verify(reservationRepo).save(reservation);
    }

    // ── Test 15c: la reservación pertenece a otro usuario ────
    @Test
    void create_withReservationOtroUser_lanzaException() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(reservationRepo.findById(78L))
                .willReturn(Optional.of(reservationVigente(78L, 999L, 2L, 1)));

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(1L, null, 2L, 7, 78L), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 15d: libro distinto al de la reservación ────────
    @Test
    void create_withBookDistintoReservation_lanzaException() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(reservationRepo.findById(79L))
                .willReturn(Optional.of(reservationVigente(79L, 1L, 2L, 1)));

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(1L, null, 8L, 7, 79L), auth))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Test 15e: la reservación ya no está vigente ──────────
    @Test
    void create_withReservationYaRetirada_lanzaException() {
        permitirCreateLoan();
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(userRepo.findByEmail("biblio@correo.com"))
                .willReturn(Optional.of(userWithId(5L)));
        given(reservationRepo.findById(80L))
                .willReturn(Optional.of(reservationVigente(80L, 1L, 2L, 3))); // RETIRADA
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusReservation(1, "PENDIENTE")));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(statusReservation(2, "LISTA_PARA_RETIRO")));

        assertThatThrownBy(() -> loanService.create(
                new LoanRequestDTO(1L, null, 2L, 7, 80L), auth))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Test 16: reporte de morosidad aplica default de limite=10 ──
    // Mismo motivo/patrón que el Test 5 (reporteLibrosMasPrestados): la
    // @Query nativeQuery de PrestamoProcedureRepository siempre manda
    // p_limite explícito, así que sin este default un null produciría
    // "LIMIT NULL" (sin límite) en Postgres.
    @Test
    void reportDelinquency_withoutLimit_aplicaDefaultDiez() {
        given(loanProcRepo.fnReportIndexDelinquency(10)).willReturn(List.of());

        loanService.reportDelinquency(null);

        verify(loanProcRepo).fnReportIndexDelinquency(10);
    }

    // ── Test 17: reporte de morosidad mapea la proyección a DTO ──
    @Test
    void reportDelinquency_withRows_mapeaProjectionADTO() {
        ReportDelinquencyProjection row = mock(ReportDelinquencyProjection.class);
        given(row.getUserId()).willReturn(3L);
        given(row.getName()).willReturn("Ana");
        given(row.getLastName()).willReturn("Pérez");
        given(row.getEmail()).willReturn("ana@correo.com");
        given(row.getAmountTotalAdeudado()).willReturn(new BigDecimal("15.50"));
        given(row.getQuantityFinesPendientes()).willReturn(2L);
        given(row.getDaysAtrasoPromedio()).willReturn(new BigDecimal("3.5"));
        given(loanProcRepo.fnReportIndexDelinquency(5)).willReturn(List.of(row));

        List<ReportDelinquencyResponseDTO> result = loanService.reportDelinquency(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(3L);
        assertThat(result.get(0).amountTotalAdeudado()).isEqualTo(new BigDecimal("15.50"));
    }

    // ── Test 18: reporte de uso con granularidad válida ──────
    @Test
    void reportUsageByPeriod_withGranularidadValida_invocaRepositoryWithValueNormalized() {
        ReportUsageByPeriodProjection row = mock(ReportUsageByPeriodProjection.class);
        given(row.getPeriod()).willReturn(Instant.now());
        given(row.getTotalLoans()).willReturn(4L);
        given(row.getTotalLoanReturns()).willReturn(2L);
        given(loanProcRepo.fnReportUsageByPeriod("semana", null, null))
                .willReturn(List.of(row));

        // "SEMANA" en mayúsculas para verificar que el service normaliza
        // (toLowerCase) antes de comparar contra la lista blanca y de
        // invocar al repositorio.
        List<ReportUsageByPeriodResponseDTO> result =
                loanService.reportUsageByPeriod("SEMANA", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalLoans()).isEqualTo(4L);
        verify(loanProcRepo).fnReportUsageByPeriod("semana", null, null);
    }

    // ── Test 19: reporte de uso con granularidad inválida ────
    @Test
    void reportUsageByPeriod_withGranularidadInvalida_lanzaException() {
        assertThatThrownBy(() -> loanService.reportUsageByPeriod("dias", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ────────────────────────────────────────────
    // lenient(): MockitoExtension usa strict stubbing por defecto -- un
    // test que solo llama getName() (ej. crear()) pero no getAuthorities()
    // haría fallar con UnnecessaryStubbingException si se stubea con
    // given() normal. Como este helper se reutiliza en tests que llaman
    // subconjuntos distintos de métodos de Authentication, se marca
    // lenient a propósito.
    //
    // doReturn(...).when(...) en vez de when(...).thenReturn(...): el tipo
    // real de Authentication.getAuthorities() es
    // Collection<? extends GrantedAuthority>, y el wildcard capturado por
    // javac exige el tipo exacto para thenReturn, no un subtipo como
    // List<SimpleGrantedAuthority>. doReturn() recibe Object y evita el
    // problema de inferencia genérica por completo.
    private Authentication authComoRole(String email, String role) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(auth).getAuthorities();
        return auth;
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Loan loanWithId(Long id) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setUserId(1L);
        loan.setBookId(2L);
        loan.setLibrarianId(5L);
        loan.setDateLoan(OffsetDateTime.now());
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().plusDays(7));
        loan.setStatusLoanId(1);
        return loan;
    }

    private StatusLoan statusLoan(Integer id, String name) {
        StatusLoan status = new StatusLoan();
        status.setId(id);
        status.setName(name);
        return status;
    }

    private StatusReservation statusReservation(Integer id, String name) {
        StatusReservation status = new StatusReservation();
        status.setId(id);
        status.setName(name);
        return status;
    }

    private Reservation reservationVigente(Long id, Long userId, Long bookId, Integer statusId) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId(userId);
        reservation.setBookId(bookId);
        reservation.setStatusReservationId(statusId);
        reservation.setDateReservation(OffsetDateTime.now());
        return reservation;
    }

    private void permitirCreateLoan() {
        lenient().when(configurationSystemService.getValueEntero("max_prestamos_usuario")).thenReturn(5);
        lenient().when(loanProcRepo.fnListLoansActivesByUser(any())).thenReturn(List.of());
    }
}
