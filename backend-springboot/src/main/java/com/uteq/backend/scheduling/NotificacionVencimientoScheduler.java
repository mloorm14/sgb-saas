package com.uteq.backend.scheduling;

import com.uteq.backend.entity.EstadoPrestamo;
import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.repository.EstadoPrestamoRepository;
import com.uteq.backend.repository.PrestamoRepository;
import com.uteq.backend.service.ConfiguracionSistemaService;
import com.uteq.backend.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job periódico del Módulo 2: detecta préstamos vigentes (ACTIVO/RENOVADO)
 * cuya {@code fecha_devolucion_estimada} cae dentro de la ventana de
 * anticipación configurada, y dispara
 * {@link NotificacionService#generarAlertaVencimiento(Prestamo)} por cada
 * uno. La deduplicación (no reenviar la misma alerta si el préstamo sigue
 * dentro de la ventana en la siguiente corrida) vive en el propio
 * {@code NotificacionService}, no aquí.
 */
@Component
public class NotificacionVencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacionVencimientoScheduler.class);
    private static final List<String> ESTADOS_PRESTAMO_VIGENTE = List.of("ACTIVO", "RENOVADO");

    private final PrestamoRepository prestamoRepo;
    private final EstadoPrestamoRepository estadoPrestamoRepo;
    private final NotificacionService notificacionService;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public NotificacionVencimientoScheduler(PrestamoRepository prestamoRepo,
                                             EstadoPrestamoRepository estadoPrestamoRepo,
                                             NotificacionService notificacionService,
                                             ConfiguracionSistemaService configuracionSistemaService) {
        this.prestamoRepo = prestamoRepo;
        this.estadoPrestamoRepo = estadoPrestamoRepo;
        this.notificacionService = notificacionService;
        this.configuracionSistemaService = configuracionSistemaService;
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void notificarProximosAVencer() {
        int diasAnticipacion = configuracionSistemaService.obtenerValorEntero("dias_anticipacion_vencimiento");
        int minutosAnticipacion = diasAnticipacion * 24 * 60;

        List<Integer> estadoIds = ESTADOS_PRESTAMO_VIGENTE.stream()
                .map(this::idDelEstado)
                .toList();

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime limite = ahora.plusMinutes(minutosAnticipacion);

        List<Prestamo> proximosAVencer = prestamoRepo
                .findByEstadoPrestamoIdInAndFechaDevolucionEstimadaBetween(estadoIds, ahora, limite);

        for (Prestamo prestamo : proximosAVencer) {
            notificacionService.generarAlertaVencimiento(prestamo);
        }

        log.info("Job de notificación de vencimiento: {} préstamos evaluados (ventana: {} días)", proximosAVencer.size(), diasAnticipacion);
    }

    private Integer idDelEstado(String nombre) {
        return estadoPrestamoRepo.findByNombre(nombre)
                .map(EstadoPrestamo::getId)
                .orElseThrow(() -> new IllegalStateException("Catalogo estados_prestamo sin fila '" + nombre + "'"));
    }
}
