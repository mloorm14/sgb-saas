package com.uteq.backend.service;

import com.uteq.backend.dto.ConfigurationSystemResponseDTO;
import com.uteq.backend.entity.AuditLogAudit;
import com.uteq.backend.entity.ConfigurationSystem;
import com.uteq.backend.repository.AuditLogAuditRepository;
import com.uteq.backend.repository.ConfigurationSystemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parámetros del sistema con cache en memoria (invalidado por clave al escribir).
 * Consumidores: usar {@code obtenerValor*} en vez del repositorio directo para no perder el cache.
 */
@Service
public class ConfigurationSystemService {

    private static final String CLAVE_NO_ENCONTRADA = "Clave de configuración no encontrada: ";
    private static final String TABLA_CONFIG = "configuracion_sistema";

    private final ConfigurationSystemRepository repo;
    private final AuditLogAuditRepository auditLogAuditRepo;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public ConfigurationSystemService(ConfigurationSystemRepository repo,
                                        AuditLogAuditRepository auditLogAuditRepo) {
        this.repo = repo;
        this.auditLogAuditRepo = auditLogAuditRepo;
    }

    @Transactional(readOnly = true)
    /**
     * Lists configuration Sistema Response DTO records.
     *
     * @return list of configuration Sistema Response data transfer object matching the requested criteria
     */
    public List<ConfigurationSystemResponseDTO> list() {
        return repo.findAll().stream()
                .map(c -> new ConfigurationSystemResponseDTO(c.getKey(), c.getValue()))
                .toList();
    }

    /**
     * Actualiza una clave que YA existe; no crea claves nuevas por esta vía.
     */
    @Transactional
    /**
     * Updates configuration Sistema Response data transfer object.
     *
     * @param key text value used to scope this configuration Sistema Response data transfer object
     * @param nuevoValor text value used to scope this configuration Sistema Response data transfer object
     * @return configuration Sistema Response data transfer object reflecting the state after the operation
     * @throws EntityNotFoundException when the configuration Sistema Response data transfer object cannot be processed with the given input
     */
    public ConfigurationSystemResponseDTO update(String key, String freshValue) {
        ConfigurationSystem config = repo.findById(key)
                .orElseThrow(() -> new EntityNotFoundException(CLAVE_NO_ENCONTRADA + key));
        config.setValue(freshValue);
        repo.save(config);
        cache.remove(key);
        registerAudit(null, null, "Actualización de configuración: " + key + " = " + freshValue);
        return new ConfigurationSystemResponseDTO(config.getKey(), config.getValue());
    }

    private void registerAudit(Long executorId, Long registrationId, String detalles) {
        AuditLogAudit event = AuditLogAudit.builder()
                .userId(executorId)
                .typeOperacion("UPDATE")
                .tableAfectada(TABLA_CONFIG)
                .registrationId(registrationId)
                .detalles(detalles)
                .dateTime(OffsetDateTime.now())
                .build();
        auditLogAuditRepo.save(event);
    }

    @Transactional(readOnly = true)
    /**
     * Retrieves configuration Sistema.
     *
     * @param key text value used to scope this configuration Sistema
     * @return resulting text payload
     * @throws EntityNotFoundException when the configuration Sistema cannot be processed with the given input
     */
    public String getValue(String key) {
        String cacheado = cache.get(key);
        if (cacheado != null) {
            return cacheado;
        }
        String value = repo.findById(key)
                .orElseThrow(() -> new EntityNotFoundException(CLAVE_NO_ENCONTRADA + key))
                .getValue();
        cache.put(key, value);
        return value;
    }

    /**
     * Retrieves configuration Sistema.
     *
     * @param key text value used to scope this configuration Sistema
     * @return identifier of the affected record
     * @throws IllegalStateException when the configuration Sistema cannot be processed with the given input
     */

    public Integer getValueEntero(String key) {
        String value = getValue(key);
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Valor no numérico para la clave '" + key + "': " + value);
        }
    }

    /**
     * Retrieves Big Decimal.
     *
     * @param key text value used to scope this Big Decimal
     * @return Big Decimal reflecting the state after the operation
     * @throws IllegalStateException when the Big Decimal cannot be processed with the given input
     */

    public BigDecimal getValueDecimal(String key) {
        String value = getValue(key);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Valor no decimal para la clave '" + key + "': " + value);
        }
    }
}
