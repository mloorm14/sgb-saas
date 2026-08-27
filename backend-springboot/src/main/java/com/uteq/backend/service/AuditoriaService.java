package com.uteq.backend.service;

import com.uteq.backend.dto.EventoAuditoriaResponseDTO;
import com.uteq.backend.dto.ResumenCategoriaAuditoriaDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    // Umbral para marcar "Revisar" en la categoría sesiones: 3 o más
    // LOGIN_FAIL en las últimas 24 horas.
    private static final long UMBRAL_LOGIN_FAIL_REVISAR = 3;

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

    /**
     * Resumen por categoría: una sola query de agregación agrupando por
     * tabla_afectada. Devuelve una lista con un elemento por cada categoría
     * que tenga al menos 1 evento en la bitácora.
     */
    @Transactional(readOnly = true)
    public List<ResumenCategoriaAuditoriaDTO> resumen() {
        OffsetDateTime desdeHoy = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<Object[]> filas = bitacoraAuditoriaRepo.resumenPorCategoria(desdeHoy);
        List<ResumenCategoriaAuditoriaDTO> resultado = new ArrayList<>();

        for (Object[] fila : filas) {
            String tablaAfectada = (String) fila[0];
            long totalEventos = (Long) fila[1];
            long eventosHoy = (Long) fila[2];
            OffsetDateTime ultimoEvento = fila[3] instanceof OffsetDateTime odt ? odt : null;

            // TODO: definir criterio de "Revisar" cuando el equipo lo defina
            boolean requiereRevision = false;
            if ("sesiones".equals(tablaAfectada)) {
                long failsRecientes = bitacoraAuditoriaRepo.contarLoginFailRecientes(
                        OffsetDateTime.now().minusHours(24));
                requiereRevision = failsRecientes >= UMBRAL_LOGIN_FAIL_REVISAR;
            }

            resultado.add(new ResumenCategoriaAuditoriaDTO(
                    tablaAfectada, totalEventos, eventosHoy, ultimoEvento, requiereRevision));
        }

        return resultado;
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
