package com.uteq.backend.scheduling;

import com.uteq.backend.entity.StatusLoan;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.service.ConfigurationSystemService;
import com.uteq.backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job periódico que alerta préstamos vigentes próximos a vencer.
 * La deduplicación vive en NotificacionService, no aquí.
 */
@Component
public class NotificationDueScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDueScheduler.class);
    private static final List<String> ESTADOS_PRESTAMO_VIGENTE = List.of("ACTIVO", "RENOVADO");

    private final LoanRepository loanRepo;
    private final StatusLoanRepository statusLoanRepo;
    private final NotificationService notificationService;
    private final ConfigurationSystemService configurationSystemService;

    public NotificationDueScheduler(LoanRepository loanRepo,
                                             StatusLoanRepository statusLoanRepo,
                                             NotificationService notificationService,
                                             ConfigurationSystemService configurationSystemService) {
        this.loanRepo = loanRepo;
        this.statusLoanRepo = statusLoanRepo;
        this.notificationService = notificationService;
        this.configurationSystemService = configurationSystemService;
    }

    @Scheduled(fixedRate = 60 * 1000)
    /**
     * Notifies notification Vencimiento Scheduler.
     */
    public void notifyNextsAExpire() {
        int daysAnticipacion = configurationSystemService.getValueEntero("dias_anticipacion_vencimiento");
        int minutesAnticipacion = daysAnticipacion * 24 * 60;

        List<Integer> statusIds = ESTADOS_PRESTAMO_VIGENTE.stream()
                .map(this::idStatus)
                .toList();

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime limit = ahora.plusMinutes(minutesAnticipacion);

        List<Loan> nextsAExpire = loanRepo
                .findByStatusLoanIdInAndDateLoanReturnEstimadaBetween(statusIds, ahora, limit);

        for (Loan loan : nextsAExpire) {
            notificationService.generateAlertaDue(loan);
        }

        log.info("Job de notificación de vencimiento: {} préstamos evaluados (ventana: {} días)", nextsAExpire.size(), daysAnticipacion);
    }

    private Integer idStatus(String name) {
        return statusLoanRepo.findByName(name)
                .map(StatusLoan::getId)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_prestamo sin fila '" + name + "'"));
    }
}
