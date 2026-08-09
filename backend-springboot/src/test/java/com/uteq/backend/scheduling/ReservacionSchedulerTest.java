package com.uteq.backend.scheduling;

import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.ReservacionProcedureRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.service.NotificacionService;
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
class ReservacionSchedulerTest {

    @Mock private ReservacionProcedureRepository reservacionProcedureRepository;
    @Mock private ReservacionRepository reservacionRepository;
    @Mock private EstadoReservacionRepository estadoReservacionRepository;
    @Mock private NotificacionService notificacionService;

    private ReservacionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReservacionScheduler(
                reservacionProcedureRepository, reservacionRepository, estadoReservacionRepository, notificacionService);

        given(estadoReservacionRepository.findByNombre("PENDIENTE")).willReturn(Optional.of(estado(1, "PENDIENTE")));
        given(estadoReservacionRepository.findByNombre("LISTA_PARA_RETIRO"))
                .willReturn(Optional.of(estado(2, "LISTA_PARA_RETIRO")));
    }

    // Los ids de PENDIENTE/LISTA_PARA_RETIRO deben resolverse por nombre
    // (no hardcodeados) y pasarse tal cual a la consulta -- mismo filtro
    // que usa internamente sp_expirar_reservaciones_vencidas.
    @Test
    void expirarReservacionesVencidas_consultaConLosEstadosPorExpirarResueltos() {
        given(reservacionRepository.findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservacionProcedureRepository.spExpirarReservacionesVencidas()).willReturn(0);

        scheduler.expirarReservacionesVencidas();

        verify(reservacionRepository).findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(
                eq(List.of(1, 2)), any(OffsetDateTime.class));
    }

    // Cada reservación por caducar dispara exactamente una notificación --
    // la dedup (si aplicara) no vive aquí, vive en NotificacionService.
    @Test
    void expirarReservacionesVencidas_notificaCadaReservacionPorCaducar() {
        Reservacion r1 = reservacionConId(10L);
        Reservacion r2 = reservacionConId(20L);
        given(reservacionRepository.findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(anyList(), any()))
                .willReturn(List.of(r1, r2));
        given(reservacionProcedureRepository.spExpirarReservacionesVencidas()).willReturn(2);

        scheduler.expirarReservacionesVencidas();

        verify(notificacionService).notificarReservaCaducada(r1);
        verify(notificacionService).notificarReservaCaducada(r2);
        verify(notificacionService, times(2)).notificarReservaCaducada(any());
    }

    @Test
    void expirarReservacionesVencidas_sinReservacionesPorCaducar_noNotificaANadie() {
        given(reservacionRepository.findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservacionProcedureRepository.spExpirarReservacionesVencidas()).willReturn(0);

        scheduler.expirarReservacionesVencidas();

        verify(notificacionService, never()).notificarReservaCaducada(any());
    }

    // El orden importa: se notifica ANTES de invocar el SP -- ver Javadoc
    // de ReservacionScheduler. Si se invirtiera, el UPDATE masivo ya habría
    // cambiado el estado antes de poder avisarle al usuario.
    @Test
    void expirarReservacionesVencidas_notificaAntesDeInvocarElProcedimiento() {
        Reservacion r1 = reservacionConId(10L);
        given(reservacionRepository.findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(anyList(), any()))
                .willReturn(List.of(r1));
        given(reservacionProcedureRepository.spExpirarReservacionesVencidas()).willReturn(1);

        scheduler.expirarReservacionesVencidas();

        InOrder orden = inOrder(notificacionService, reservacionProcedureRepository);
        orden.verify(notificacionService).notificarReservaCaducada(r1);
        orden.verify(reservacionProcedureRepository).spExpirarReservacionesVencidas();
    }

    // El SP siempre se invoca, incluso sin reservaciones por caducar en
    // este minuto -- es el que efectivamente marca EXPIRADA cualquier fila
    // que la consulta no haya visto (ninguna razón para saltárselo).
    @Test
    void expirarReservacionesVencidas_invocaElProcedimientoSiempre() {
        given(reservacionRepository.findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(anyList(), any()))
                .willReturn(List.of());
        given(reservacionProcedureRepository.spExpirarReservacionesVencidas()).willReturn(0);

        scheduler.expirarReservacionesVencidas();

        verify(reservacionProcedureRepository).spExpirarReservacionesVencidas();
    }

    private EstadoReservacion estado(Integer id, String nombre) {
        EstadoReservacion estado = new EstadoReservacion();
        estado.setId(id);
        estado.setNombre(nombre);
        return estado;
    }

    private Reservacion reservacionConId(Long id) {
        Reservacion reservacion = new Reservacion();
        reservacion.setId(id);
        reservacion.setUsuarioId(1L);
        reservacion.setLibroId(2L);
        reservacion.setFechaLimiteRetiro(OffsetDateTime.now().minusHours(1));
        return reservacion;
    }
}
