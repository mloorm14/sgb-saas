package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupSchedule;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BackupScheduleRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
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
public class BackupScheduleService {

    private final BackupScheduleRepository progRepo;
    private final UserRepository userRepo;
    private final TaskScheduler taskScheduler;
    private final BackupService backupService;

    @Value("${app.backup.r2.bucket:}")
    private String bucket;

    private final Map<Long, ScheduledFuture<?>> schedulesActives = new HashMap<>();

    public BackupScheduleService(BackupScheduleRepository progRepo,
                                      UserRepository userRepo,
                                      TaskScheduler taskScheduler,
                                      BackupService backupService) {
        this.progRepo = progRepo;
        this.userRepo = userRepo;
        this.taskScheduler = taskScheduler;
        this.backupService = backupService;
    }

    /**
     * Revive las programaciones activas al arrancar el servidor.
     * Sin esto, un reinicio/suspensión (Render free) borra los
     * TaskScheduler en memoria y los automáticos dejan de correr.
     */
    @PostConstruct
    /**
     * Initializes Backup schedule.
     */
    public void initializeTasksProgramadas() {
        progRepo.findByActiveTrueOrderByLastExecutionDesc().forEach(p -> {
            try {
                scheduleExecution(p.getId());
            } catch (Exception e) {
                System.err.println("No se pudo reprogramar backup id=" + p.getId() + ": " + e.getMessage());
            }
        });
    }

    // ---------- CRUD simples ----------

    /**
     * Lists Backup schedule records.
     *
     * @return list of Backup schedule matching the requested criteria
     */

    public List<BackupSchedule> listActives() {
        return progRepo.findByActiveTrueOrderByLastExecutionDesc();
    }

    /**
     * Retrieves Backup schedule.
     *
     * @param id numeric identifier used to scope this Backup schedule
     * @return Backup schedule reflecting the state after the operation
     * @throws ResponseStatusException when the Backup schedule cannot be processed with the given input
     */

    public BackupSchedule get(Long id) {
        return progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
    }

    /**
     * Creates Backup schedule.
     *
     * @param dto Backup schedule used to scope this Backup schedule
     * @return Backup schedule reflecting the state after the operation
     */

    public BackupSchedule create(BackupSchedule dto) {
        validateXorFields(dto);
        Long userId = getUserCurrentId();
        dto.setCreatedBy(userId);
        dto.setCreated(OffsetDateTime.now());
        dto.setActive(true);
        return progRepo.save(dto);
    }

    /**
     * Updates Backup schedule.
     *
     * @param id numeric identifier used to scope this Backup schedule
     * @param date date-time bound used to scope this Backup schedule
     * @throws ResponseStatusException when the Backup schedule cannot be processed with the given input
     */

    public void updateLastExecution(Long id, OffsetDateTime date) {
        BackupSchedule existing = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        existing.setLastExecution(date);
        progRepo.save(existing);
    }

    /**
     * Deletes Backup schedule.
     *
     * @param id numeric identifier used to scope this Backup schedule
     * @throws ResponseStatusException when the Backup schedule cannot be processed with the given input
     */

    public void delete(Long id) {
        BackupSchedule p = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        p.setActive(false);
        progRepo.save(p);
        // Cancelar el scheduler activo para que deje de ejecutarse de inmediato.
        cancelSchedule(id);
    }

    // ---------- Validación XOR ----------

    private void validateXorFields(BackupSchedule dto) {
        long count = ((dto.getEveryTimes() != null) ? 1L : 0L) + ((dto.getEveryDays() != null) ? 1L : 0L);
        if (count != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe definirse exactamente uno de: cada_horas o cada_dias");
        }
        if (dto.getEveryTimes() != null && (dto.getEveryTimes() < 1 || dto.getEveryTimes() > 23)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cada_horas debe estar entre 1 y 23");
        }
        if (dto.getEveryDays() != null && (dto.getEveryDays() < 1 || dto.getEveryDays() > 30)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cada_dias debe estar entre 1 y 30");
        }
    }

    // ---------- Utilidades ----------

    private Long getUserCurrentId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return 1L;
        // Sin reflexión: el principal es User de Spring (sin campo id).
        // Se resuelve por correo, igual que el resto de services.
        try {
            return userRepo.findByEmail(auth.getName()).map(User::getId).orElse(1L);
        } catch (Exception ex) {
            return 1L;
        }
    }

    // ---------- Programación automática ----------

    /**
     * Programa o reprograma una tarea de respaldo automático.
     * Si ya había un scheduler activo para este id, se cancela y se crea uno nuevo.
     */
    public ScheduledFuture<?> scheduleExecution(Long id) {
        BackupSchedule p = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!Boolean.TRUE.equals(p.getActive())) return null;

        long intervalSeconds;
        if (p.getEveryTimes() != null) {
            intervalSeconds = p.getEveryTimes() * 3600L;
        } else {
            intervalSeconds = p.getEveryDays() * 86400L;
        }

        // Calcular delay inicial hasta la próxima ejecución
        long initialDelay = computeDelayUntilNextExecution(p);

        // Cancelar cualquier scheduler previo para este id
        cancelSchedule(id);

        // Programar con fixedDelay
        long delayMs = initialDelay * 1000L;
        ScheduledFuture<?> sf = taskScheduler.scheduleAtFixedRate(
                () -> executeBackupProgramado(id),
                Instant.now().plusMillis(delayMs),
                java.time.Duration.ofSeconds(intervalSeconds)
        );

        schedulesActives.put(id, sf);
        // Actualizar última ejecución programada
        p.setLastExecution(OffsetDateTime.now());
        progRepo.save(p);
        return sf;
    }

    private long computeDelayUntilNextExecution(BackupSchedule p) {
        // Retorna segundos hasta la próxima ejecución usando java.time.Duration.
        // Antes retornaba minutos pero el llamador multiplicaba por 1000 como si fueran
        // segundos, lo que dejaba el scheduler 60 veces desfasado.
        OffsetDateTime ahora = OffsetDateTime.now();
        long seconds;
        if (p.getEveryTimes() != null) {
            // Próxima hora en punto: si son las 14:32, ejecutar a las 15:00.
            OffsetDateTime nextTime = ahora.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            seconds = Duration.between(ahora, nextTime).getSeconds();
        } else {
            // Cada X días: próxima medianoche local.
            OffsetDateTime nextMedianoche = ahora.toLocalDate().plusDays(1)
                    .atStartOfDay().atOffset(ahora.getOffset());
            seconds = Duration.between(ahora, nextMedianoche).getSeconds();
        }
        return Math.max(seconds, 1L);
    }

    private void cancelSchedule(Long id) {
        ScheduledFuture<?> sf = schedulesActives.remove(id);
        if (sf != null) {
            sf.cancel(false);
        }
    }

    /**
     * Ejecuta el backup inmediato según la programación configurada.
     * Genera el zip y lo sube a R2, guarda registro en tabla 'backups'.
     */
    private void executeBackupProgramado(Long id) {
        BackupSchedule p = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!Boolean.TRUE.equals(p.getActive())) {
            cancelSchedule(id);
            return;
        }

        // Usar tablas configuradas en la programación; si no hay, usar el set por defecto
        Set<String> tablesDefault = (p.getTables() != null && !p.getTables().isEmpty())
                ? p.getTables()
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
            OffsetDateTime from;
            OffsetDateTime until;

            if (p.getEveryTimes() != null) {
                // Cada X horas: desde hace X horas hasta ahora
                from = ahora.minusHours(p.getEveryTimes());
                until = ahora;
            } else {
                // Cada X días: desde hace X días hasta ahora (siempre empieza en 00:00 del día actual-ish)
                long days = p.getEveryDays();
                from = ahora.minusDays(days).withHour(0).withMinute(0).withSecond(0).withNano(0);
                until = ahora.withHour(23).withMinute(59).withSecond(59);
            }

            Backup backup = backupService.generateBackup(from, until, tablesDefault, p.getFormat(), "automatico");
            // Registrar programación última ejecución
            p.setLastExecution(backup.getCreated());
            progRepo.save(p);
        } catch (Exception e) {
            // Log error pero no fallar el scheduler
            System.err.println("Error executing programmed backup id=" + id + ": " + e.getMessage());
        }
    }
}