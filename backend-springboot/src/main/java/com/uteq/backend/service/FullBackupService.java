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
         * Busca/lista recursos.
     * @return lista o pagina de resultados
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
     * Actualiza update configuration con las reglas de negocio requeridas por el flujo.
     *
     * @param frequencyTimes valor de entrada frequencyTimes usado por la operacion para completar su regla de negocio
     * @param daysRetention valor de entrada daysRetention usado por la operacion para completar su regla de negocio
     * @param enabled valor de entrada enabled usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
         * Lista los registros de backup filtrados por tipo.
     *
     * @param type tipo de backup a filtrar (ej. 'completo', 'incremental')
     * @return lista de registros ordenados por inicio descendente
     */
    public List<RegistrationBackup> listByType(String type) {
        return registrationRepo.findByTypeOrderByStartedDesc(type);
    }

    /**
         * Lista todos los backups registrados.
     * @return lista de backups ordenados por fecha descendente
     */

    public List<RegistrationBackup> listAll() {
        return registrationRepo.findAll();
    }

    @Transactional
    /**
         * Elimina un registro de backup y su archivo en almacenamiento.
     *
     * @param id identificador del registro a eliminar
     * @throws ResponseStatusException si el registro no existe (404)
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
     * Genera o entrega download a partir de los datos actuales del sistema.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return contenido binario generado o recuperado por la operacion
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
     * Registra register start validando los datos de entrada antes de persistir cambios.
     *
     * @param type criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param executedBy valor de entrada executedBy usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Registra register result validando los datos de entrada antes de persistir cambios.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param status criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param nameFile valor de entrada nameFile usado por la operacion para completar su regla de negocio
     * @param sizeBytes valor de entrada sizeBytes usado por la operacion para completar su regla de negocio
     * @param pathR2 valor de entrada pathR2 usado por la operacion para completar su regla de negocio
     * @param messageError valor de entrada messageError usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
