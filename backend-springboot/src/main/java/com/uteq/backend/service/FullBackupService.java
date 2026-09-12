package com.uteq.backend.service;

import com.uteq.backend.entity.ConfigurationBackup;
import com.uteq.backend.entity.RegistrationBackup;
import com.uteq.backend.repository.ConfigurationBackupRepository;
import com.uteq.backend.repository.RegistrationBackupRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class FullBackupService {

    private final ConfigurationBackupRepository configRepo;
    private final RegistrationBackupRepository registrationRepo;
    private final UserRepository userRepository;

    private final BackupStorageService storageService;

    public FullBackupService(ConfigurationBackupRepository configRepo,
                                   RegistrationBackupRepository registrationRepo,
                                   UserRepository userRepository,
                                   BackupStorageService storageService) {
        this.configRepo = configRepo;
        this.registrationRepo = registrationRepo;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    // ── Configuración ────────────────────────────────────────────────────────
    /**
     * Retrieves configuration backup.
     *
     * @return configuration backup reflecting the state after the operation
     */
    public ConfigurationBackup getConfiguration() {
        return configRepo.findAll().stream().findFirst().orElseGet(() -> {
            ConfigurationBackup config = ConfigurationBackup.builder()
                    .enabled(false)
                    .frequencyTimes(6)
                    .daysRetention(14)
                    .build();
            return configRepo.save(config);
        });
    }

    @Transactional
    /**
     * Updates configuration backup.
     *
     * @param frecuenciaHoras numeric value used to scope this configuration backup
     * @param diasRetencion numeric value used to scope this configuration backup
     * @param habilitado flag used to scope this configuration backup
     * @return configuration backup reflecting the state after the operation
     * @throws ResponseStatusException when the configuration backup cannot be processed with the given input
     */
    public ConfigurationBackup updateConfiguration(Integer frequencyTimes, Integer daysRetention, Boolean enabled) {
        if (frequencyTimes != null && (frequencyTimes < 1 || frequencyTimes > 168)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frecuenciaHoras debe estar entre 1 y 168");
        }
        if (daysRetention != null && (daysRetention < 1 || daysRetention > 90)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diasRetencion debe estar entre 1 y 90");
        }
        ConfigurationBackup config = getConfiguration();
        if (frequencyTimes != null) config.setFrequencyTimes(frequencyTimes);
        if (daysRetention != null) config.setDaysRetention(daysRetention);
        if (enabled != null) config.setEnabled(enabled);
        config.setUpdatedBy(getUserCurrentId());
        config.setUpdated(OffsetDateTime.now());
        if (Boolean.TRUE.equals(config.getEnabled())) {
            config.setNextExecution(OffsetDateTime.now().plusHours(config.getFrequencyTimes()));
        }
        return configRepo.save(config);
    }

    // ── Historial de registros ────────────────────────────────────────────────
    /**
     * Lists record backup records.
     *
     * @param type text value used to scope this record backup records
     * @return list of record backup matching the requested criteria
     */
    public List<RegistrationBackup> listByType(String type) {
        return registrationRepo.findByTypeOrderByStartedDesc(type);
    }

    /**
     * Lists record backup records.
     *
     * @return list of record backup matching the requested criteria
     */

    public List<RegistrationBackup> listAll() {
        return registrationRepo.findAll();
    }

    @Transactional
    /**
     * Deletes backup Completo.
     *
     * @param id numeric identifier used to scope this backup Completo
     * @throws ResponseStatusException when the backup Completo cannot be processed with the given input
     */
    public void delete(Long id) {
        RegistrationBackup r = registrationRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        if (r.getPathR2() != null) {
            try {
                String key = extractKey(r.getPathR2());
                storageService.delete(key);
            } catch (Exception ignored) {
            }
        }
        registrationRepo.delete(r);
    }

    /**
     * Downloads backup Completo.
     *
     * @param id numeric identifier used to scope this backup Completo
     * @return binary content of the generated file
     * @throws ResponseStatusException when the backup Completo cannot be processed with the given input
     */

    public byte[] download(Long id) {
        RegistrationBackup r = registrationRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        if (r.getPathR2() == null || r.getPathR2().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay archivo asociado a este registro");
        }
        String key = extractKey(r.getPathR2());
        return storageService.download(key);
    }

    private String extractKey(String path) {
        // Node.js retorna "s3://bucket/backups/..." o una ruta local.
        if (path.startsWith("s3://")) {
            String withoutEsquema = path.substring(5);
            int idx = withoutEsquema.indexOf('/');
            if (idx != -1 && idx < withoutEsquema.length() - 1) {
                return withoutEsquema.substring(idx + 1);
            }
        }
        return path;
    }

    // ── Registro de ejecución (llamado desde el microservicio Node.js via token interno) ──
    @Transactional
    /**
     * Registers record backup.
     *
     * @param type text value used to scope this record backup
     * @param ejecutadoPor numeric identifier used to scope this record backup
     * @return record backup reflecting the state after the operation
     */
    public RegistrationBackup registerStart(String type, Long executedBy) {
        RegistrationBackup r = RegistrationBackup.builder()
                .type(type)
                .status("ejecutando")
                .executedBy(executedBy)
                .build();
        return registrationRepo.save(r);
    }

    @Transactional
    /**
     * Registers record backup.
     *
     * @param id numeric identifier used to scope this record backup
     * @param estado text value used to scope this record backup
     * @param nombreArchivo text value used to scope this record backup
     * @param tamanoBytes numeric identifier used to scope this record backup
     * @param rutaR2 text value used to scope this record backup
     * @param mensajeError text value used to scope this record backup
     * @return record backup reflecting the state after the operation
     * @throws ResponseStatusException when the record backup cannot be processed with the given input
     */
    public RegistrationBackup registerResult(Long id, String status, String nameFile,
                                               Long sizeBytes, String pathR2, String messageError) {
        RegistrationBackup r = registrationRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado: " + id));
        r.setStatus(status);
        r.setNameFile(nameFile);
        r.setSizeFileBytes(sizeBytes);
        r.setPathR2(pathR2);
        r.setMessageError(messageError);
        r.setFinished(OffsetDateTime.now());
        // Si fue exitoso, actualizar la configuración con la última ejecución
        if ("exitoso".equals(status)) {
            configRepo.findAll().stream().findFirst().ifPresent(config -> {
                config.setLastExecution(r.getFinished());
                config.setNextExecution(r.getFinished().plusHours(config.getFrequencyTimes()));
                configRepo.save(config);
            });
        }
        return registrationRepo.save(r);
    }

    private Long getUserCurrentId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return userRepository.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
