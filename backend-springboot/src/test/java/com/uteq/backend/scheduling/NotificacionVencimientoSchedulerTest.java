package com.uteq.backend.scheduling;

import com.uteq.backend.entity.EstadoPrestamo;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class NotificacionVencimientoSchedulerTest {

    @Mock private PrestamoRepository prestamoRepo;
    @Mock private EstadoPrestamoRepository estadoPrestamoRepo;
    @Mock private NotificacionService notificacionService;

    private NotificacionVencimientoScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificacionVencimientoScheduler(prestamoRepo, estadoPrestamoRepo, notificacionService);
        ReflectionTestUtils.setField(scheduler, "minutosAnticipacion", 15);

        given(estadoPrestamoRepo.findByNombre("ACTIVO")).willReturn(Optional.of(estado(1, "ACTIVO")));
        given(estadoPrestamoRepo.findByNombre("RENOVADO")).willReturn(Optional.of(estado(2, "RENOVADO")));
    }

    // Los ids de ACTIVO/RENOVADO deben resolverse por nombre (no
    // hardcodeados) y pasarse tal cual a la consulta de la ventana.
    @Test
    void notificarProximosAVencer_consultaConLosEstadosVigentesResueltos() {
        given(prestamoRepo.findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of());

        scheduler.notificarProximosAVencer();

        verify(prestamoRepo).findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(
                eq(List.of(1, 2)), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    // Cada préstamo dentro de la ventana dispara exactamente una llamada a
    // NotificacionService -- la dedup real vive ahí, no en el scheduler.
    @Test
    void notificarProximosAVencer_delegaCadaPrestamoEnNotificacionService() {
        Prestamo p1 = prestamoConId(1L);
        Prestamo p2 = prestamoConId(2L);
        given(prestamoRepo.findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of(p1, p2));

        scheduler.notificarProximosAVencer();

        verify(notificacionService).generarAlertaVencimiento(p1);
        verify(notificacionService).generarAlertaVencimiento(p2);
        verify(notificacionService, times(2)).generarAlertaVencimiento(any());
    }

    @Test
    void notificarProximosAVencer_sinPrestamosEnLaVentana_noLlamaANotificacionService() {
        given(prestamoRepo.findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(
                anyList(), any(), any())).willReturn(List.of());

        scheduler.notificarProximosAVencer();

        verify(notificacionService, never()).generarAlertaVencimiento(any());
    }

    private EstadoPrestamo estado(Integer id, String nombre) {
        EstadoPrestamo estado = new EstadoPrestamo();
        estado.setId(id);
        estado.setNombre(nombre);
        return estado;
    }

    private Prestamo prestamoConId(Long id) {
        Prestamo prestamo = new Prestamo();
        prestamo.setId(id);
        prestamo.setUsuarioId(1L);
        prestamo.setLibroId(2L);
        prestamo.setFechaDevolucionEstimada(OffsetDateTime.now().plusMinutes(10));
        return prestamo;
    }
}
