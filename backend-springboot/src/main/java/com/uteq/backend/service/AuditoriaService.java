package com.uteq.backend.service;

import com.uteq.backend.dto.EventoAuditoriaResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta de {@code bitacora_auditoria} para GERENTE/ADMIN (Módulo 6). La
 * tabla y el repositorio ya existían (escritos desde el Módulo de
 * autenticación y, desde el Módulo 5, por {@code UsuarioAdminService}) --
 * lo que faltaba era exponerlos de lectura.
 */
@Service
public class AuditoriaService {

    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    private final UsuarioRepository usuarioRepo;

    public AuditoriaService(BitacoraAuditoriaRepository bitacoraAuditoriaRepo,
                             UsuarioRepository usuarioRepo) {
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional(readOnly = true)
    public Page<EventoAuditoriaResponseDTO> listar(Long usuarioId, String modulo,
                                                     OffsetDateTime desde, OffsetDateTime hasta,
                                                     Pageable pageable) {
        Page<BitacoraAuditoria> pagina = bitacoraAuditoriaRepo.buscarConFiltros(
                usuarioId, modulo, desde, hasta, pageable);

        // Resolver correo por id en un solo IN (...) en vez de una consulta
        // por fila -- evita N+1 sobre una página de hasta N eventos.
        // BitacoraAuditoria.usuarioId no es una relación @ManyToOne (ver
        // Javadoc de la entidad: "columnas planas, sin joins"), así que
        // este mapeo se resuelve acá, no con un JOIN FETCH en el repositorio.
        Set<Long> idsUsuarios = pagina.getContent().stream()
                .map(BitacoraAuditoria::getUsuarioId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> correoPorId = usuarioRepo.findAllById(idsUsuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getCorreo));

        return pagina.map(evento -> toDTO(evento, correoPorId));
    }

    private EventoAuditoriaResponseDTO toDTO(BitacoraAuditoria evento, Map<Long, String> correoPorId) {
        String correo = evento.getUsuarioId() == null ? null : correoPorId.get(evento.getUsuarioId());
        return new EventoAuditoriaResponseDTO(
                evento.getId(),
                correo,
                evento.getTipoOperacion(),
                evento.getFechaHora(),
                evento.getTablaAfectada(),
                evento.getDetalles());
    }
}
