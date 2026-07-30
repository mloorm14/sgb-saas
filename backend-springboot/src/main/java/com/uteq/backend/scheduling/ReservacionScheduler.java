package com.uteq.backend.scheduling;

import com.uteq.backend.repository.ReservacionProcedureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que expira en lote las reservaciones vencidas no
 * retiradas, invocando sp_expirar_reservaciones_vencidas (ver
 * docs/basedatos/CATALOGO-SP.md #6). No hay endpoint manual de
 * expiración a propósito -- ver Javadoc de ReservacionService.
 */
@Component
public class ReservacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservacionScheduler.class);

    private final ReservacionProcedureRepository reservacionProcedureRepository;

    public ReservacionScheduler(ReservacionProcedureRepository reservacionProcedureRepository) {
        this.reservacionProcedureRepository = reservacionProcedureRepository;
    }

    // Cada 15 minutos -- valor que podra ser modificado mas adelante si es necesario
    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void expirarReservacionesVencidas() {
        Integer filasActualizadas = reservacionProcedureRepository.spExpirarReservacionesVencidas();
        log.info("Job de expiración de reservaciones: {} filas actualizadas", filasActualizadas);
    }
}