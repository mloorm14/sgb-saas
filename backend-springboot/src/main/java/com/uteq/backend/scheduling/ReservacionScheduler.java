package com.uteq.backend.scheduling;

import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.ReservacionProcedureRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job periódico que expira en lote las reservaciones vencidas no
 * retiradas, invocando sp_expirar_reservaciones_vencidas (ver
 * docs/basedatos/CATALOGO-SP.md #6). No hay endpoint manual de
 * expiración a propósito -- ver Javadoc de ReservacionService.
 * <p>
 * Módulo 2 (notificaciones): {@code sp_expirar_reservaciones_vencidas}
 * solo devuelve un conteo de filas actualizadas, no CUÁLES -- no alcanza
 * para notificar individualmente después del UPDATE. Por eso este
 * scheduler consulta primero, con el mismo filtro exacto que usa la
 * función (ver {@code ReservacionRepository
 * #findByEstadoReservacionIdInAndFechaLimiteRetiroBefore}), notifica cada
 * una, y solo entonces invoca la función para el UPDATE masivo real. Se
 * acepta como riesgo menor (no resuelto acá) que, en la ventana entre la
 * consulta y el UPDATE, otro proceso pudiera alterar alguna fila -- este
 * proyecto corre una sola instancia del scheduler, así que en la práctica
 * no ocurre.
 */
@Component
public class ReservacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservacionScheduler.class);
    private static final List<String> ESTADOS_RESERVA_POR_EXPIRAR = List.of("PENDIENTE", "LISTA_PARA_RETIRO");

    private final ReservacionProcedureRepository reservacionProcedureRepository;
    private final ReservacionRepository reservacionRepository;
    private final EstadoReservacionRepository estadoReservacionRepository;
    private final NotificacionService notificacionService;

    public ReservacionScheduler(ReservacionProcedureRepository reservacionProcedureRepository,
                                ReservacionRepository reservacionRepository,
                                EstadoReservacionRepository estadoReservacionRepository,
                                NotificacionService notificacionService) {
        this.reservacionProcedureRepository = reservacionProcedureRepository;
        this.reservacionRepository = reservacionRepository;
        this.estadoReservacionRepository = estadoReservacionRepository;
        this.notificacionService = notificacionService;
    }

    // Cada 15 minutos -- valor que podra ser modificado mas adelante si es necesario
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void expirarReservacionesVencidas() {
        notificarLasQueVanAExpirar();

        Integer filasActualizadas = reservacionProcedureRepository.spExpirarReservacionesVencidas();
        log.info("Job de expiración de reservaciones: {} filas actualizadas", filasActualizadas);
    }

    private void notificarLasQueVanAExpirar() {
        List<Integer> estadoIds = ESTADOS_RESERVA_POR_EXPIRAR.stream()
                .map(this::idDelEstado)
                .toList();

        List<Reservacion> porExpirar = reservacionRepository
                .findByEstadoReservacionIdInAndFechaLimiteRetiroBefore(estadoIds, OffsetDateTime.now());

        for (Reservacion reservacion : porExpirar) {
            notificacionService.notificarReservaCaducada(reservacion);
        }
    }

    private Integer idDelEstado(String nombre) {
        return estadoReservacionRepository.findByNombre(nombre)
                .map(EstadoReservacion::getId)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_reservacion sin fila '" + nombre + "'"));
    }
}