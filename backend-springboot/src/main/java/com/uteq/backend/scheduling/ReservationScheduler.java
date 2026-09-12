package com.uteq.backend.scheduling;

import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.ReservationProcedureRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job periódico que expira en lote las reservaciones vencidas no retiradas.
 * Notifica cada una antes del UPDATE masivo; corre en una sola instancia.
 */
@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);
    private static final List<String> ESTADOS_RESERVA_POR_EXPIRAR = List.of("PENDIENTE", "LISTA_PARA_RETIRO");

    private final ReservationProcedureRepository reservationProcedureRepository;
    private final ReservationRepository reservationRepository;
    private final StatusReservationRepository statusReservationRepository;
    private final NotificationService notificationService;

    public ReservationScheduler(ReservationProcedureRepository reservationProcedureRepository,
                                ReservationRepository reservationRepository,
                                StatusReservationRepository statusReservationRepository,
                                NotificationService notificationService) {
        this.reservationProcedureRepository = reservationProcedureRepository;
        this.reservationRepository = reservationRepository;
        this.statusReservationRepository = statusReservationRepository;
        this.notificationService = notificationService;
    }

    // Cada 15 minutos -- valor que podra ser modificado mas adelante si es necesario
    @Scheduled(fixedRate = 15 * 60 * 1000)
    /**
     * Handles expirar reservations Vencidas.
     */
    public void expireReservationsVencidas() {
        notifyQueVanAExpire();

        Integer rowsUpdated = reservationProcedureRepository.spExpireReservationsVencidas();
        log.info("Job de expiración de reservaciones: {} filas actualizadas", rowsUpdated);
    }

    private void notifyQueVanAExpire() {
        List<Integer> statusIds = ESTADOS_RESERVA_POR_EXPIRAR.stream()
                .map(this::idStatus)
                .toList();

        List<Reservation> byExpire = reservationRepository
                .findByStatusReservationIdInAndDateLimitPickupBefore(statusIds, OffsetDateTime.now());

        for (Reservation reservation : byExpire) {
            notificationService.notifyReservationExpired(reservation);
        }
    }

    private Integer idStatus(String name) {
        return statusReservationRepository.findByName(name)
                .map(StatusReservation::getId)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_reservacion sin fila '" + name + "'"));
    }
}