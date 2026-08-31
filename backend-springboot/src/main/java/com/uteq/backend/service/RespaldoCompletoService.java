package com.uteq.backend.service;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import com.uteq.backend.entity.RegistroRespaldo;
import com.uteq.backend.repository.ConfiguracionRespaldoRepository;
import com.uteq.backend.repository.RegistroRespaldoRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RespaldoCompletoService {

    private final ConfiguracionRespaldoRepository configRepo;
    private final RegistroRespaldoRepository registroRepo;
    private final UsuarioRepository usuarioRepository;

    public RespaldoCompletoService(ConfiguracionRespaldoRepository configRepo,
                                   RegistroRespaldoRepository registroRepo,
                                   UsuarioRepository usuarioRepository) {
        this.configRepo = configRepo;
        this.registroRepo = registroRepo;
        this.usuarioRepository = usuarioRepository;
    }

    // ── Configuración ────────────────────────────────────────────────────────
    public ConfiguracionRespaldo obtenerConfiguracion() {
        return configRepo.findAll().stream().findFirst().orElseGet(() -> {
            ConfiguracionRespaldo config = ConfiguracionRespaldo.builder()
                    .habilitado(false)
                    .frecuenciaHoras(6)
                    .diasRetencion(14)
                    .build();
            return configRepo.save(config);
        });
    }

    @Transactional
    public ConfiguracionRespaldo actualizarConfiguracion(Integer frecuenciaHoras, Integer diasRetencion, Boolean habilitado) {
        if (frecuenciaHoras != null && (frecuenciaHoras < 1 || frecuenciaHoras > 168)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "frecuenciaHoras debe estar entre 1 y 168");
        }
        if (diasRetencion != null && (diasRetencion < 1 || diasRetencion > 90)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "diasRetencion debe estar entre 1 y 90");
        }
        ConfiguracionRespaldo config = obtenerConfiguracion();
        if (frecuenciaHoras != null) config.setFrecuenciaHoras(frecuenciaHoras);
        if (diasRetencion != null) config.setDiasRetencion(diasRetencion);
        if (habilitado != null) config.setHabilitado(habilitado);
        config.setActualizadoPor(obtenerUsuarioActualId());
        config.setActualizadoEn(OffsetDateTime.now());
        if (Boolean.TRUE.equals(config.getHabilitado())) {
            config.setProximaEjecucion(OffsetDateTime.now().plusHours(config.getFrecuenciaHoras()));
        }
        return configRepo.save(config);
    }

    // ── Historial de registros ────────────────────────────────────────────────
    public List<RegistroRespaldo> listarPorTipo(String tipo) {
        return registroRepo.findByTipoOrderByIniciadoEnDesc(tipo);
    }

    public List<RegistroRespaldo> listarTodos() {
        return registroRepo.findAll();
    }

    // ── Registro de ejecución (llamado desde el microservicio Node.js via token interno) ──
    @Transactional
    public RegistroRespaldo registrarInicio(String tipo, Long ejecutadoPor) {
        RegistroRespaldo r = RegistroRespaldo.builder()
                .tipo(tipo)
                .estado("ejecutando")
                .ejecutadoPor(ejecutadoPor)
                .build();
        return registroRepo.save(r);
    }

    @Transactional
    public RegistroRespaldo registrarResultado(Long id, String estado, String nombreArchivo,
                                               Long tamanoBytes, String rutaR2, String mensajeError) {
        RegistroRespaldo r = registroRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado: " + id));
        r.setEstado(estado);
        r.setNombreArchivo(nombreArchivo);
        r.setTamanoArchivoBytes(tamanoBytes);
        r.setRutaR2(rutaR2);
        r.setMensajeError(mensajeError);
        r.setFinalizadoEn(OffsetDateTime.now());
        // Si fue exitoso, actualizar la configuración con la última ejecución
        if ("exitoso".equals(estado)) {
            configRepo.findAll().stream().findFirst().ifPresent(config -> {
                config.setUltimaEjecucion(r.getFinalizadoEn());
                config.setProximaEjecucion(r.getFinalizadoEn().plusHours(config.getFrecuenciaHoras()));
                configRepo.save(config);
            });
        }
        return registroRepo.save(r);
    }

    private Long obtenerUsuarioActualId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return usuarioRepository.findByCorreo(auth.getName()).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
