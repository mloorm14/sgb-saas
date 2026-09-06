package com.uteq.backend.service;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ejecuta respaldos completos (pg_dump -Fc + subida a R2) dentro del backend
 * (reemplaza al microservicio Node.js backup-service/).
 * <p>
 * Cerrojo en memoria con auto-liberación por tiempo: si una ejecución supera
 * {@code app.backup.max-minutes} (default 20), el siguiente intento la da por
 * muerta y procede. Como el timeout de pg_dump (5 min) es menor, en la
 * práctica el cerrojo siempre se libera por la vía normal.
 */
@Service
public class RespaldoCompletoEjecutor {

    private static final Logger log = LoggerFactory.getLogger(RespaldoCompletoEjecutor.class);

    private final RespaldoCompletoService respaldoService;
    private final BackupStorageService storageService;
    private final PgDumpRunner pgDumpRunner;
    private final TaskScheduler taskScheduler;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${app.backup.max-minutes:20}")
    private long maxMinutos;

    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final AtomicLong inicioMs = new AtomicLong(0);

    public RespaldoCompletoEjecutor(RespaldoCompletoService respaldoService,
                                    BackupStorageService storageService,
                                    PgDumpRunner pgDumpRunner,
                                    TaskScheduler taskScheduler) {
        this.respaldoService = respaldoService;
        this.storageService = storageService;
        this.pgDumpRunner = pgDumpRunner;
        this.taskScheduler = taskScheduler;
    }

    /** Disparo manual (botón): responde 202 de inmediato, ejecuta en background. */
    public void dispararManual() {
        adquirirCerrojo();
        taskScheduler.schedule(() -> {
            try {
                ejecutar("manual", null);
            } finally {
                liberarCerrojo();
            }
        }, java.time.Instant.now());
    }

    /** Disparo del cron horario: lee configuracion_respaldo con guard anti-rancia. */
    public void ejecutarAutomatico() {
        ConfiguracionRespaldo config = respaldoService.obtenerConfiguracion();
        if (!Boolean.TRUE.equals(config.getHabilitado())) return;

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime proxima = config.getProximaEjecucion();
        int frecuencia = config.getFrecuenciaHoras() != null ? config.getFrecuenciaHoras() : 6;

        // Guard anti-rancia: si la próxima quedó muy atrás (sueño largo de
        // Render), no disparar en ráfaga; se recalcula y se espera al ciclo.
        if (proxima != null && proxima.isBefore(ahora.minusHours((long) frecuencia * 2))) {
            log.warn("Cron de respaldo con proxima_ejecucion rancia ({}), se recalcula sin disparar", proxima);
            respaldoService.actualizarConfiguracion(frecuencia, null, true);
            return;
        }
        if (proxima != null && ahora.isBefore(proxima)) return;

        try {
            adquirirCerrojo();
        } catch (RespaldoEnCursoException e) {
            log.info("Backup automático omitido: ya hay uno en curso");
            return;
        }
        try {
            ejecutar("automatico", config.getActualizadoPor());
        } finally {
            liberarCerrojo();
        }
    }

    private void adquirirCerrojo() {
        long ahora = System.currentTimeMillis();
        long inicio = inicioMs.get();
        if (enCurso.get() && inicio > 0 && (ahora - inicio) > maxMinutos * 60 * 1000L) {
            log.warn("Watchdog: cerrojo de respaldo con {} min, se libera y procede",
                    (ahora - inicio) / 60000);
            liberarCerrojo();
        }
        if (!enCurso.compareAndSet(false, true)) {
            throw new RespaldoEnCursoException("Ya hay un respaldo en ejecucion");
        }
        inicioMs.set(System.currentTimeMillis());
    }

    private void liberarCerrojo() {
        enCurso.set(false);
        inicioMs.set(0);
    }

    void ejecutar(String tipo, Long ejecutadoPor) {
        RegistroRespaldo registro = respaldoService.registrarInicio(tipo, ejecutadoPor);
        Path temporal = null;
        try {
            String nombre = "backup_completo_"
                    + DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC)
                            .format(java.time.Instant.now())
                    + ".dump";
            temporal = Files.createTempFile("respaldo_", ".dump");

            pgDumpRunner.dump(temporal, dbUrl, dbUser, dbPassword);

            byte[] datos = Files.readAllBytes(temporal);
            storageService.upload(nombre, datos);
            String ruta = storageService.rutaPara(nombre);

            respaldoService.registrarResultado(registro.getId(), "exitoso", nombre,
                    (long) datos.length, ruta, null);
            log.info("Backup {} exitoso: {}", tipo, nombre);
        } catch (Exception e) {
            log.error("Error en backup {}", tipo, e);
            String detalle = e.getMessage() != null ? e.getMessage() : e.toString();
            respaldoService.registrarResultado(registro.getId(), "fallido", null, null, null,
                    detalle.substring(0, Math.min(detalle.length(), 500)));
        } finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); } catch (Exception ignored) {}
            }
        }
    }
}
