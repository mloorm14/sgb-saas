package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupProgramacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BackupProgramacionRepository;
import com.uteq.backend.repository.BackupRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

@Service
public class BackupProgramacionService {

    private final BackupProgramacionRepository progRepo;
    private final BackupRepository backupRepo;
    private final UsuarioRepository usuarioRepo;
    private final TaskScheduler taskScheduler;
    private final BackupService backupService;
    private final BackupStorageService storageService;

    @Value("${app.backup.r2.bucket:}")
    private String bucket;

    private final Map<Long, ScheduledFuture<?>> programacionesActivas = new HashMap<>();

    public BackupProgramacionService(BackupProgramacionRepository progRepo,
                                     BackupRepository backupRepo,
                                     UsuarioRepository usuarioRepo,
                                     TaskScheduler taskScheduler,
                                     BackupService backupService,
                                     BackupStorageService storageService) {
        this.progRepo = progRepo;
        this.backupRepo = backupRepo;
        this.usuarioRepo = usuarioRepo;
        this.taskScheduler = taskScheduler;
        this.backupService = backupService;
        this.storageService = storageService;
    }

    // ---------- CRUD simples ----------

    public List<BackupProgramacion> listarActivas() {
        return progRepo.findByActivoTrueOrderByUltimaEjecucionDesc();
    }

    public BackupProgramacion obtener(Long id) {
        return progRepo.findById(id)
                .filter(p -> p.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
    }

    public BackupProgramacion crear(BackupProgramacion dto) {
        validarXorCampos(dto);
        Long userId = obtenerUsuarioActualId();
        dto.setCreadoPor(userId);
        dto.setCreadoEn(OffsetDateTime.now());
        dto.setActivo(true);
        return progRepo.save(dto);
    }

    public void actualizarUltimaEjecucion(Long id, OffsetDateTime fecha) {
        BackupProgramacion existing = progRepo.findById(id)
                .filter(P -> P.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        existing.setUltimaEjecucion(fecha);
        progRepo.save(existing);
    }

    public void eliminar(Long id) {
        BackupProgramacion p = progRepo.findById(id)
                .filter(P -> P.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        p.setActivo(false);
        progRepo.save(p);
        // Cancelar scheduler si existe
        programacionesActivas.remove(id);
    }

    // ---------- Validación XOR ----------

    private void validarXorCampos(BackupProgramacion dto) {
        long count = ((dto.getCadaHoras() != null) ? 1 : 0) + ((dto.getCadaDias() != null) ? 1 : 0);
        if (count != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe definirse exactamente uno de: cada_horas o cada_dias");
        }
        if (dto.getCadaHoras() != null && (dto.getCadaHoras() < 1 || dto.getCadaHoras() > 23)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cada_horas debe estar entre 1 y 23");
        }
        if (dto.getCadaDias() != null && (dto.getCadaDias() < 1 || dto.getCadaDias() > 30)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cada_dias debe estar entre 1 y 30");
        }
    }

    // ---------- Utilidades ----------

    private Long obtenerUsuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return 1L;
        Object principal = auth.getPrincipal();
        try {
            var field = principal.getClass().getDeclaredField("id");
            field.setAccessible(true);
            return (Long) field.get(principal);
        } catch (Exception e) {
            try {
                return usuarioRepo.findByCorreo(auth.getName()).map(Usuario::getId).orElse(1L);
            } catch (Exception ex) {
                return 1L;
            }
        }
    }

    // ---------- Programación automática ----------

    /**
     * Programa o reprograma una tarea de respaldo automático.
     * Si ya había un scheduler activo para este id, se cancela y se crea uno nuevo.
     */
    public ScheduledFuture<?> programarEjecucion(Long id) {
        BackupProgramacion p = progRepo.findById(id)
                .filter(P -> P.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!p.isActivo()) return null;

        long intervalSeconds;
        if (p.getCadaHoras() != null) {
            intervalSeconds = p.getCadaHoras() * 3600L;
        } else {
            intervalSeconds = p.getCadaDias() * 86400L;
        }

        // Calcular delay inicial hasta la próxima ejecución
        long initialDelay = calcularDelayHastaProximoEjecucion(p);

        // Cancelar cualquier scheduler previo para este id
        cancelarProgramacion(id);

        // Programar con fixedDelay
        long delayMs = initialDelay * 1000L;
        ScheduledFuture<?> sf = taskScheduler.scheduleAtFixedRate(
                () -> ejecutarBackupProgramado(id),
                Instant.now().plusMillis(delayMs),
                java.time.Duration.ofSeconds(intervalSeconds)
        );

        programacionesActivas.put(id, sf);
        // Actualizar última ejecución programada
        p.setUltimaEjecucion(OffsetDateTime.now());
        progRepo.save(p);
        return sf;
    }

    private long calcularDelayHastaProximoEjecucion(BackupProgramacion p) {
        // Si es cadaHoras, queremos que empiece en la próxima hora en punto (00 minutos)
        // Si es cadaDias, queremos que empiece en 00:00 del día siguiente o mismo si ya pasó las 00:00
        if (p.getCadaHoras() != null) {
            // Próxima hora en punto: si son las 14:32, delay = a las 15:00 = 28min = 1680s
            long nowMinute = OffsetDateTime.now().getMinute();
            long nowSecond = OffsetDateTime.now().getSecond();
            long nowNano = OffsetDateTime.now().getNano();
            long minutesToNextHour = 60 - nowMinute;
            long secondsInFuture = minutesToNextHour * 60 - nowSecond - (nowNano / 1_000_000);
            if (secondsInFuture < 0) secondsInFuture += 3600 * 60;
            return secondsInFuture / 60; // en minutos
        } else {
            // Cada X días: delay hasta la próxima medianoche (00:00)
            long nowHour = OffsetDateTime.now().getHour();
            long nowMinute = OffsetDateTime.now().getMinute();
            long nowSecond = OffsetDateTime.now().getSecond();
            long secondsUntilMidnight = (24 - nowHour) * 3600 - nowMinute * 60 - nowSecond;
            if (secondsUntilMidnight < 0) secondsUntilMidnight += 24 * 3600;
            return secondsUntilMidnight / 60; // minutos
        }
    }

    private void cancelarProgramacion(Long id) {
        ScheduledFuture<?> sf = programacionesActivas.remove(id);
        if (sf != null) {
            sf.cancel(false);
        }
    }

    /**
     * Ejecuta el backup inmediato según la programación configurada.
     * Genera el zip y lo sube a R2, guarda registro en tabla 'backups'.
     */
    private void ejecutarBackupProgramado(Long id) {
        BackupProgramacion p = progRepo.findById(id)
                .filter(P -> P.isActivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!p.isActivo()) {
            cancelarProgramacion(id);
            return;
        }

        // Usar tablas configuradas en la programación; si no hay, usar el set por defecto
        Set<String> tablasDefault = (p.getTablas() != null && !p.getTablas().isEmpty())
                ? p.getTablas()
                : Set.of(
                        "prestamos", "reservas", "reservaciones", "multas", "libros", "usuarios",
                        "bitacora_auditoria", "auditoria", "configuracion_sistema",
                        "notificaciones", "favoritos", "sugerencias_adquisicion", "categorias", "autores"
                );

        try {
            // Usar horario fijo 00:00-23:59 del día calculado
            // Para cadaHoras: usamos el rango desde la hora actual hasta + cadaHoras
            // Para cadaDias: usamos desde 00:00 hace X días hasta ahora
            OffsetDateTime ahora = OffsetDateTime.now();
            OffsetDateTime desde, hasta;

            if (p.getCadaHoras() != null) {
                // Cada X horas: desde hace X horas hasta ahora
                desde = ahora.minusHours(p.getCadaHoras());
                hasta = ahora;
            } else {
                // Cada X días: desde hace X días hasta ahora (siempre empieza en 00:00 del día actual-ish)
                long dias = p.getCadaDias();
                desde = ahora.minusDays(dias).withHour(0).withMinute(0).withSecond(0).withNano(0);
                hasta = ahora.withHour(23).withMinute(59).withSecond(59);
            }

            Backup backup = backupService.generarBackup(desde, hasta, tablasDefault, p.getFormato(), "automatico");
            // Registrar programación última ejecución
            p.setUltimaEjecucion(backup.getCreadoEn());
            progRepo.save(p);
        } catch (Exception e) {
            // Log error pero no fallar el scheduler
            System.err.println("Error executing programmed backup id=" + id + ": " + e.getMessage());
        }
    }
}