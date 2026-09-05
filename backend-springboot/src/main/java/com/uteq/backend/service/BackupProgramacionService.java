package com.uteq.backend.service;

import com.uteq.backend.entity.Backup;
import com.uteq.backend.entity.BackupProgramacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BackupProgramacionRepository;
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
    private final UsuarioRepository usuarioRepo;
    private final TaskScheduler taskScheduler;
    private final BackupService backupService;

    @Value("${app.backup.r2.bucket:}")
    private String bucket;

    private final Map<Long, ScheduledFuture<?>> programacionesActivas = new HashMap<>();

    public BackupProgramacionService(BackupProgramacionRepository progRepo,
                                      UsuarioRepository usuarioRepo,
                                      TaskScheduler taskScheduler,
                                      BackupService backupService) {
        this.progRepo = progRepo;
        this.usuarioRepo = usuarioRepo;
        this.taskScheduler = taskScheduler;
        this.backupService = backupService;
    }

    // ---------- CRUD simples ----------

    public List<BackupProgramacion> listarActivas() {
        return progRepo.findByActivoTrueOrderByUltimaEjecucionDesc();
    }

    public BackupProgramacion obtener(Long id) {
        return progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActivo()))
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
                .filter(prog -> Boolean.TRUE.equals(prog.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        existing.setUltimaEjecucion(fecha);
        progRepo.save(existing);
    }

    public void eliminar(Long id) {
        BackupProgramacion p = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));
        p.setActivo(false);
        progRepo.save(p);
        // Cancelar el scheduler activo para que deje de ejecutarse de inmediato.
        cancelarProgramacion(id);
    }

    // ---------- Validación XOR ----------

    private void validarXorCampos(BackupProgramacion dto) {
        long count = ((dto.getCadaHoras() != null) ? 1L : 0L) + ((dto.getCadaDias() != null) ? 1L : 0L);
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
        // Sin reflexión: el principal es User de Spring (sin campo id).
        // Se resuelve por correo, igual que el resto de services.
        try {
            return usuarioRepo.findByCorreo(auth.getName()).map(Usuario::getId).orElse(1L);
        } catch (Exception ex) {
            return 1L;
        }
    }

    // ---------- Programación automática ----------

    /**
     * Programa o reprograma una tarea de respaldo automático.
     * Si ya había un scheduler activo para este id, se cancela y se crea uno nuevo.
     */
    public ScheduledFuture<?> programarEjecucion(Long id) {
        BackupProgramacion p = progRepo.findById(id)
                .filter(prog -> Boolean.TRUE.equals(prog.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!Boolean.TRUE.equals(p.getActivo())) return null;

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
        // Retorna segundos hasta la próxima ejecución usando java.time.Duration.
        // Antes retornaba minutos pero el llamador multiplicaba por 1000 como si fueran
        // segundos, lo que dejaba el scheduler 60 veces desfasado.
        OffsetDateTime ahora = OffsetDateTime.now();
        long segundos;
        if (p.getCadaHoras() != null) {
            // Próxima hora en punto: si son las 14:32, ejecutar a las 15:00.
            OffsetDateTime proximaHora = ahora.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            segundos = Duration.between(ahora, proximaHora).getSeconds();
        } else {
            // Cada X días: próxima medianoche local.
            OffsetDateTime proximaMedianoche = ahora.toLocalDate().plusDays(1)
                    .atStartOfDay().atOffset(ahora.getOffset());
            segundos = Duration.between(ahora, proximaMedianoche).getSeconds();
        }
        return Math.max(segundos, 1L);
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
                .filter(prog -> Boolean.TRUE.equals(prog.getActivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programación no encontrada " + id));

        if (!Boolean.TRUE.equals(p.getActivo())) {
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
            OffsetDateTime desde;
            OffsetDateTime hasta;

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