package com.uteq.backend.scheduling;

import com.uteq.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Job periódico que elimina cuentas de usuarios cuyo correo no fue
 * verificado dentro de las últimas 24 horas. Sigue el patrón de
 * {@link ReservacionScheduler}.
 */
@Component
public class UsuarioScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsuarioScheduler.class);
    private static final long HORAS_EXPIRACION = 24;

    private final UsuarioRepository usuarioRepo;

    public UsuarioScheduler(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    @Scheduled(fixedRate = 60 * 60 * 1000) // cada 1 hora
    @Transactional
    public void eliminarNoVerificadosVencidos() {
        Instant cutoff = Instant.now().minus(HORAS_EXPIRACION, ChronoUnit.HOURS);
        int eliminados = usuarioRepo.deleteNoVerificadosBefore(cutoff);
        if (eliminados > 0) {
            log.info("Auto-eliminación de usuarios no verificados: {} cuentas eliminadas", eliminados);
        }
    }
}
