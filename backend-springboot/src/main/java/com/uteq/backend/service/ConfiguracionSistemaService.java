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
 * Parámetros del sistema (tabla {@code configuracion_sistema}), pensados
 * para leerse en cada préstamo/reserva/multa sin ir a la base de datos cada
 * vez (Módulo 9.4 del roadmap). Cache en memoria simple (no Redis): un
 * {@link ConcurrentHashMap} por instancia de la app, invalidado clave por
 * clave al escribir — suficiente porque este valor cambia con muy poca
 * frecuencia (un Admin ajustando un parámetro puntual), a diferencia del
 * cache "libros" (ver RedisConfig), que sí necesita compartirse entre
 * instancias e invalidarse por TTL.
 *
 * IMPORTANTE para consumidores futuros (Ramas C/D/E del roadmap: límite de
 * renovaciones, caducidad de reservas, etc.): usar
 * {@link #obtenerValor(String)}/{@link #obtenerValorEntero(String)}/
 * {@link #obtenerValorDecimal(String)} en vez de inyectar
 * {@link ConfiguracionSistemaRepository} directo, para no perder el cache.
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
    public List<ConfiguracionSistemaResponseDTO> listar() {
        return repo.findAll().stream()
                .map(c -> new ConfiguracionSistemaResponseDTO(c.getClave(), c.getValor()))
                .toList();
    }

    /**
     * Actualiza el valor de una clave que YA existe. A propósito no crea
     * claves nuevas por esta vía: el catálogo de parámetros válidos lo
     * define el dominio (ver seed.sql / migraciones), no quien llama al
     * endpoint — evita que quede una clave suelta que ningún consumidor
     * real lee.
     */
    @Transactional
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

    public Integer obtenerValorEntero(String clave) {
        String valor = obtenerValor(clave);
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Valor no numérico para la clave '" + clave + "': " + valor);
        }
    }

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
