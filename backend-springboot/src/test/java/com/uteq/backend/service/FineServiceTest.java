package com.uteq.backend.service;

import com.uteq.backend.dto.FineActionResponseDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineProcedureRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.repository.projection.SummaryFinancialFinesProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FineServiceTest {

    @Mock FineRepository fineRepo;
    @Mock FineProcedureRepository fineProcRepo;
    @Mock UserRepository userRepo;
    @Mock BookRepository bookRepo;
    @Mock LoanRepository loanRepo;

    @InjectMocks FineService fineService;

    // ── Test 1: pagar multa invoca el SP y traduce el Map ────
    @Test
    void pay_invocaProcedimientoYRetornaDTO() {
        Map<String, Object> mapaResult = new HashMap<>();
        mapaResult.put("o_multa_id", 7L);
        mapaResult.put("o_usuario_desbloqueado", true);
        given(fineProcRepo.spPayFine(7L)).willReturn(mapaResult);

        FineActionResponseDTO result = fineService.pay(7L);

        assertThat(result.fineId()).isEqualTo(7L);
        assertThat(result.userUnblocked()).isTrue();
    }

    // ── Test 2: pagar multa que NO desbloquea al usuario (tiene otras pendientes) ──
    @Test
    void pay_withOtrasFinesPendientes_notDesbloqueaUser() {
        Map<String, Object> mapaResult = new HashMap<>();
        mapaResult.put("o_multa_id", 8L);
        mapaResult.put("o_usuario_desbloqueado", false);
        given(fineProcRepo.spPayFine(8L)).willReturn(mapaResult);

        FineActionResponseDTO result = fineService.pay(8L);

        assertThat(result.userUnblocked()).isFalse();
    }

    // ── Test 3: anular con rol GERENTE -> resuelve rolEjecutor del token, no del body ──
    @Test
    void void_withRoleManager_resuelveRoleFromAuthentication() {
        Authentication auth = authComoRole("gerente@correo.com", "GERENTE");
        Map<String, Object> mapaResult = new HashMap<>();
        mapaResult.put("o_multa_id", 9L);
        mapaResult.put("o_usuario_desbloqueado", true);
        given(fineProcRepo.spVoidFine(9L, "Error administrativo", "GERENTE"))
                .willReturn(mapaResult);

        FineActionResponseDTO result = fineService.annul(9L, "Error administrativo", auth);

        assertThat(result.fineId()).isEqualTo(9L);
        // Verifica explícitamente que el rol enviado al SP es el de la
        // authority real ("GERENTE"), no un valor que hubiera podido venir
        // de un campo del body (que ni siquiera existe en el DTO).
        verify(fineProcRepo).spVoidFine(9L, "Error administrativo", "GERENTE");
    }

    // ── Test 4: anular con rol ADMIN también es válido ────────
    @Test
    void void_withRoleAdmin_resuelveRoleAdmin() {
        Authentication auth = authComoRole("admin@correo.com", "ADMIN");
        Map<String, Object> mapaResult = new HashMap<>();
        mapaResult.put("o_multa_id", 12L);
        mapaResult.put("o_usuario_desbloqueado", false);
        given(fineProcRepo.spVoidFine(12L, "Duplicado", "ADMIN"))
                .willReturn(mapaResult);

        fineService.annul(12L, "Duplicado", auth);

        verify(fineProcRepo).spVoidFine(12L, "Duplicado", "ADMIN");
    }

    // ── Test 6: anular sin rol válido -> defensa en profundidad, denegado ──
    // (Escenario que en teoría @PreAuthorize del controller ya bloquea,
    // pero el service lo revalida por su cuenta -- ver Javadoc de
    // MultaService.resolverRolAnulacion.)
    @Test
    void void_withoutRoleManagerOAdmin_lanzaAccessDenegado() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");

        assertThatThrownBy(() -> fineService.annul(9L, "motivo", auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: acceso denegado cuando un LECTOR pide multas de otro usuario ──
    @Test
    void listByUser_cuandoReaderPideOtroUser_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));

        assertThatThrownBy(() -> fineService.listByUser(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 7: resumen financiero mapea la proyección a DTO ──
    @Test
    void reportSummaryFinancial_withData_mapeaProjectionADTO() {
        SummaryFinancialFinesProjection row = mock(SummaryFinancialFinesProjection.class);
        given(row.getTotalRecaudado()).willReturn(new BigDecimal("125.00"));
        given(row.getTotalPending()).willReturn(new BigDecimal("40.50"));

        SummaryFinancialFinesProjection today = mock(SummaryFinancialFinesProjection.class);
        given(today.getTotalRecaudado()).willReturn(new BigDecimal("10.00"));
        given(today.getTotalPending()).willReturn(new BigDecimal("5.00"));
        lenient().when(fineProcRepo.fnReportSummaryFinancial(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(today);
        lenient().when(fineProcRepo.fnPaymentsRecientes(anyInt())).thenReturn(List.of());

        OffsetDateTime from = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime until = OffsetDateTime.parse("2026-08-31T23:59:59Z");
        given(fineProcRepo.fnReportSummaryFinancial(from, until)).willReturn(row);

        SummaryFinancialFinesResponseDTO result = fineService.reportSummaryFinancial(from, until);

        assertThat(result.totalRecaudado()).isEqualTo(new BigDecimal("125.00"));
        assertThat(result.totalPending()).isEqualTo(new BigDecimal("40.50"));
    }

    // ── Test 8: resumen financiero sin rango de fechas envía null tal cual ──
    @Test
    void reportSummaryFinancial_withoutRangeDates_envianullRepository() {
        SummaryFinancialFinesProjection row = mock(SummaryFinancialFinesProjection.class);
        given(row.getTotalRecaudado()).willReturn(BigDecimal.ZERO);
        given(row.getTotalPending()).willReturn(BigDecimal.ZERO);
        given(fineProcRepo.fnReportSummaryFinancial(null, null)).willReturn(row);

        SummaryFinancialFinesProjection today = mock(SummaryFinancialFinesProjection.class);
        given(today.getTotalRecaudado()).willReturn(BigDecimal.ZERO);
        given(today.getTotalPending()).willReturn(BigDecimal.ZERO);
        lenient().when(fineProcRepo.fnReportSummaryFinancial(any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(today);
        lenient().when(fineProcRepo.fnPaymentsRecientes(anyInt())).thenReturn(List.of());

        fineService.reportSummaryFinancial(null, null);

        verify(fineProcRepo).fnReportSummaryFinancial(null, null);
    }

    // ── Helpers ────────────────────────────────────────────
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
}
