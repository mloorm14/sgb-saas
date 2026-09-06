package com.uteq.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron horario del respaldo completo (reemplaza al node-cron del
 * microservicio). Lee {@code configuracion_respaldo}; el guard anti-rancia
 * vive en {@link RespaldoCompletoEjecutor#ejecutarAutomatico()}.
 */
@Component
public class RespaldoCompletoScheduler {

    private static final Logger log = LoggerFactory.getLogger(RespaldoCompletoScheduler.class);

    private final RespaldoCompletoEjecutor ejecutor;
    private final RespaldoCompletoService respaldoService;

    public RespaldoCompletoScheduler(RespaldoCompletoEjecutor ejecutor,
                                     RespaldoCompletoService respaldoService) {
        this.ejecutor = ejecutor;
        this.respaldoService = respaldoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void limpiarAtascadosAlArrancar() {
        int n = respaldoService.marcarAtascadosComoFallidos();
        if (n > 0) log.info("Auto-limpieza: {} respaldo(s) atascado(s) marcado(s) como fallido", n);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void tickHorario() {
        try {
            ejecutor.ejecutarAutomatico();
        } catch (Exception e) {
            log.error("Error en cron de respaldo completo", e);
        }
    }
}
