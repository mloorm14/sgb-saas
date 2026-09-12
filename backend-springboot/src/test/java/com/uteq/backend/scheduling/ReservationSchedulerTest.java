package com.uteq.backend.scheduling;

import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.ReservationProcedureRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationSchedulerTest {

    @Mock private ReservationProcedureRepository reservationProcedureRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private StatusReservationRepository statusReservationRepository;
    @Mock private NotificationService notificationService;

    private ReservationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReservationScheduler(
                reservationProcedureRepository, reservationRepository, statusReservationRepository, notificationService);

        given(statusReservationRepository.findByName("PENDIENTE")).willReturn(Optional.of(status(1, "PENDIENTE")));
        given(statusReservationRepository.findByName("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(status(2, "LISTA_PARA_RETIRO")));
    }

    // Los ids de PENDIENTE/LISTA_PARA_RETIRO deben resolverse por nombre
    // (no hardcodeados) y pasarse tal cual a la consulta -- mismo filtro
    // que usa internamente sp_expirar_reservaciones_vencidas.
    @Test
    void expireReservationsVencidas_consultaWithStatusesByExpireResueltos() {
        given(reservationRepository.findByStatusReservationIdInAndDateLimitPickupBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservationProcedureRepository.spExpireReservationsVencidas()).willReturn(0);

        scheduler.expireReservationsVencidas();

        verify(reservationRepository).findByStatusReservationIdInAndDateLimitPickupBefore(
                eq(List.of(1, 2)), any(OffsetDateTime.class));
    }

    // Cada reservación por caducar dispara exactamente una notificación --
    // la dedup (si aplicara) no vive aquí, vive en NotificacionService.
    @Test
    void expireReservationsVencidas_notificaEveryReservationByLapse() {
        Reservation r1 = reservationWithId(10L);
        Reservation r2 = reservationWithId(20L);
        given(reservationRepository.findByStatusReservationIdInAndDateLimitPickupBefore(anyList(), any()))
                .willReturn(List.of(r1, r2));
        given(reservationProcedureRepository.spExpireReservationsVencidas()).willReturn(2);

        scheduler.expireReservationsVencidas();

        verify(notificationService).notifyReservationExpired(r1);
        verify(notificationService).notifyReservationExpired(r2);
        verify(notificationService, times(2)).notifyReservationExpired(any());
    }

    @Test
    void expireReservationsVencidas_withoutReservationsByLapse_notNotificaANadie() {
        given(reservationRepository.findByStatusReservationIdInAndDateLimitPickupBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservationProcedureRepository.spExpireReservationsVencidas()).willReturn(0);

        scheduler.expireReservationsVencidas();

        verify(notificationService, never()).notifyReservationExpired(any());
    }

    // El orden importa: se notifica ANTES de invocar el SP -- ver Javadoc
    // de ReservacionScheduler. Si se invirtiera, el UPDATE masivo ya habría
    // cambiado el estado antes de poder avisarle al usuario.
    @Test
    void expireReservationsVencidas_notificaAntesInvocarProcedimiento() {
        Reservation r1 = reservationWithId(10L);
        given(reservationRepository.findByStatusReservationIdInAndDateLimitPickupBefore(anyList(), any()))
                .willReturn(List.of(r1));
        given(reservationProcedureRepository.spExpireReservationsVencidas()).willReturn(1);

        scheduler.expireReservationsVencidas();

        InOrder order = inOrder(notificationService, reservationProcedureRepository);
        order.verify(notificationService).notifyReservationExpired(r1);
        order.verify(reservationProcedureRepository).spExpireReservationsVencidas();
    }

    // El SP siempre se invoca, incluso sin reservaciones por caducar en
    // este minuto -- es el que efectivamente marca EXPIRADA cualquier fila
    // que la consulta no haya visto (ninguna razón para saltárselo).
    @Test
    void expireReservationsVencidas_invocaProcedimientoSiempre() {
        given(reservationRepository.findByStatusReservationIdInAndDateLimitPickupBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservationProcedureRepository.spExpireReservationsVencidas()).willReturn(0);

        scheduler.expireReservationsVencidas();

        verify(reservationProcedureRepository).spExpireReservationsVencidas();
    }

    private StatusReservation status(Integer id, String name) {
        StatusReservation status = new StatusReservation();
        status.setId(id);
        status.setName(name);
        return status;
    }

    private Reservation reservationWithId(Long id) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId(1L);
        reservation.setBookId(2L);
        reservation.setDateLimitPickup(OffsetDateTime.now().minusHours(1));
        return reservation;
    }
}
