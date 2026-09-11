package com.uteq.backend.service;

import com.uteq.backend.dto.ConfiguracionSistemaResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.ConfiguracionSistema;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.ConfiguracionSistemaRepository;
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
public class ConfiguracionSistemaService {

    private static final String CLAVE_NO_ENCONTRADA = "Clave de configuración no encontrada: ";
    private static final String TABLA_CONFIG = "configuracion_sistema";

    private final ConfiguracionSistemaRepository repo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public ConfiguracionSistemaService(ConfiguracionSistemaRepository repo,
                                        BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.repo = repo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional(readOnly = true)
    /**
     * Executes the listar operation.
     * @return operation result
     */
    public List<ConfiguracionSistemaResponseDTO> listar() {
        return repo.findAll().stream()
                .map(c -> new ConfiguracionSistemaResponseDTO(c.getClave(), c.getValor()))
                .toList();
    }

    /**
     * Actualiza una clave que YA existe; no crea claves nuevas por esta vía.
     */
    @Transactional
    /**
     * Executes the actualizar operation.
     * @param clave value required by the operation
     * @param nuevoValor value required by the operation
     * @return operation result
     */
    public ConfiguracionSistemaResponseDTO actualizar(String clave, String nuevoValor) {
        ConfiguracionSistema config = repo.findById(clave)
                .orElseThrow(() -> new EntityNotFoundException(CLAVE_NO_ENCONTRADA + clave));
        config.setValor(nuevoValor);
        repo.save(config);
        cache.remove(clave);
        registrarAuditoria(null, null, "Actualización de configuración: " + clave + " = " + nuevoValor);
        return new ConfiguracionSistemaResponseDTO(config.getClave(), config.getValor());
    }

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_CONFIG)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }

    @Transactional(readOnly = true)
    /**
     * Executes the obtenerValor operation.
     * @param clave value required by the operation
     * @return operation result
     */
    public String obtenerValor(String clave) {
        String cacheado = cache.get(clave);
        if (cacheado != null) {
            return cacheado;
        }
        String valor = repo.findById(clave)
                .orElseThrow(() -> new EntityNotFoundException(CLAVE_NO_ENCONTRADA + clave))
                .getValor();
        cache.put(clave, valor);
        return valor;
    }

    /**

     * Executes the obtenerValorEntero operation.

     * @param clave value required by the operation

     * @return operation result

     */

    public Integer obtenerValorEntero(String clave) {
        String valor = obtenerValor(clave);
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Valor no numérico para la clave '" + clave + "': " + valor);
        }
    }

    /**

     * Executes the obtenerValorDecimal operation.

     * @param clave value required by the operation

     * @return operation result

     */

    public BigDecimal obtenerValorDecimal(String clave) {
        String valor = obtenerValor(clave);
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Valor no decimal para la clave '" + clave + "': " + valor);
        }
    }
}
