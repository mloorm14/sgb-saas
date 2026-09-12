package com.uteq.backend.scheduling;

import com.uteq.backend.entity.StatusLoan;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.service.ConfigurationSystemService;
import com.uteq.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDueSchedulerTest {

    @Mock private LoanRepository loanRepo;
    @Mock private StatusLoanRepository statusLoanRepo;
    @Mock private NotificationService notificationService;
    @Mock private ConfigurationSystemService configurationSystemService;

    private NotificationDueScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationDueScheduler(loanRepo, statusLoanRepo, notificationService, configurationSystemService);

        given(configurationSystemService.getValueEntero("dias_anticipacion_vencimiento")).willReturn(1);
        given(statusLoanRepo.findByName("ACTIVO")).willReturn(Optional.of(status(1, "ACTIVO")));
        given(statusLoanRepo.findByName("RENOVADO")).willReturn(Optional.of(status(2, "RENOVADO")));
    }

    // Los ids de ACTIVO/RENOVADO deben resolverse por nombre (no
    // hardcodeados) y pasarse tal cual a la consulta de la ventana.
    @Test
    void notifyNextsAExpire_consultaWithStatusesVigentesResueltos() {
        given(loanRepo.findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of());

        scheduler.notifyNextsAExpire();

        verify(loanRepo).findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(
                eq(List.of(1, 2)), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    // Cada préstamo dentro de la ventana dispara exactamente una llamada a
    // NotificacionService -- la dedup real vive ahí, no en el scheduler.
    @Test
    void notifyNextsAExpire_delegaEveryLoanNotificationService() {
        Loan p1 = loanWithId(1L);
        Loan p2 = loanWithId(2L);
        given(loanRepo.findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of(p1, p2));

        scheduler.notifyNextsAExpire();

        verify(notificationService).generateAlertaDue(p1);
        verify(notificationService).generateAlertaDue(p2);
        verify(notificationService, times(2)).generateAlertaDue(any());
    }

    @Test
    void notifyNextsAExpire_withoutLoansVentana_notLlamaANotificationService() {
        given(loanRepo.findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of());

        scheduler.notifyNextsAExpire();

        verify(notificationService, never()).generateAlertaDue(any());
    }

    private StatusLoan status(Integer id, String name) {
        StatusLoan status = new StatusLoan();
        status.setId(id);
        status.setName(name);
        return status;
    }

    private Loan loanWithId(Long id) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setUserId(1L);
        loan.setBookId(2L);
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().plusMinutes(10));
        return loan;
    }
}
