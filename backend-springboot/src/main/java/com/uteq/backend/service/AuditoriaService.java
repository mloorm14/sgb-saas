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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta de {@code bitacora_auditoria} para GERENTE/ADMIN.
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
    /**
     * Executes the listar operation.
     * @param usuarioId value required by the operation
     * @param modulo value required by the operation
     * @param desde value required by the operation
     * @param hasta value required by the operation
     * @param pageable value required by the operation
     * @return operation result
     */
    public Page<EventoAuditoriaResponseDTO> listar(Long usuarioId, String modulo,
                                                     OffsetDateTime desde, OffsetDateTime hasta,
                                                     Pageable pageable) {
        Page<BitacoraAuditoria> pagina = bitacoraAuditoriaRepo.buscarConFiltros(
                usuarioId, modulo, desde, hasta, pageable);

        // Resuelve correo por id con un solo IN (...) para evitar N+1.
        Set<Long> idsUsuarios = pagina.getContent().stream()
                .map(BitacoraAuditoria::getUsuarioId)
                .filter(Objects::nonNull)
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
    /**
     * Executes the resumen operation.
     * @return operation result
     */
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

    /**

     * Executes the exportarCsv operation.

     * @param usuarioId value required by the operation

     * @param modulo value required by the operation

     * @param desde value required by the operation

     * @param hasta value required by the operation

     * @return operation result

     */

    public byte[] exportarCsv(Long usuarioId, String modulo, OffsetDateTime desde, OffsetDateTime hasta) {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10000, org.springframework.data.domain.Sort.by("fecha_hora").descending());
        var page = bitacoraAuditoriaRepo.buscarConFiltros(usuarioId, modulo, desde, hasta, pageable);
        StringBuilder sb = new StringBuilder();
        sb.append("id,usuarioId,tipoOperacion,tablaAfectada,fechaHora,detalles\n");
        for (var e : page.getContent()) {
            sb.append(e.getId()).append(",").append(e.getUsuarioId()).append(",").append(escape(e.getTipoOperacion())).append(",").append(escape(e.getTablaAfectada())).append(",").append(e.getFechaHora()).append(",").append(escape(e.getDetalles())).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private String escape(String s) { if (s==null) return ""; String t=s.replace("\"","\"\""); if (t.contains(",")||t.contains("\n")||t.contains("\"")) return "\""+t+"\""; return t; }

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
