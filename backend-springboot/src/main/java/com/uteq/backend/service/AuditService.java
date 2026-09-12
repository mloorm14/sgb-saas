package com.uteq.backend.service;

import com.uteq.backend.dto.EventAuditResponseDTO;
import com.uteq.backend.dto.SummaryCategoryAuditDTO;
import com.uteq.backend.entity.AuditLogAudit;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.AuditLogAuditRepository;
import com.uteq.backend.repository.UserRepository;
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
public class AuditService {

    // Umbral para marcar "Revisar" en la categoría sesiones: 3 o más
    // LOGIN_FAIL en las últimas 24 horas.
    private static final long UMBRAL_LOGIN_FAIL_REVISAR = 3;

    private final AuditLogAuditRepository auditLogAuditRepo;
    private final UserRepository userRepo;

    public AuditService(AuditLogAuditRepository auditLogAuditRepo,
                             UserRepository userRepo) {
        this.auditLogAuditRepo = auditLogAuditRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    /**
     * Lists Evento audit record Response DTO records.
     *
     * @param userId numeric identifier used to scope this Evento audit record Response DTO records
     * @param module text value used to scope this Evento audit record Response DTO records
     * @param from date-time bound used to scope this Evento audit record Response DTO records
     * @param until date-time bound used to scope this Evento audit record Response DTO records
     * @param pageable pagination information used to scope this Evento audit record Response DTO records
     * @return page of Evento audit record Response data transfer object for the requested pagination
     */
    public Page<EventAuditResponseDTO> list(Long userId, String module,
                                                     OffsetDateTime from, OffsetDateTime until,
                                                     Pageable pageable) {
        Page<AuditLogAudit> page = auditLogAuditRepo.searchWithFilters(
                userId, module, from, until, pageable);

        // Resuelve correo por id con un solo IN (...) para evitar N+1.
        Set<Long> idsUsers = page.getContent().stream()
                .map(AuditLogAudit::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> emailById = userRepo.findAllById(idsUsers).stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        return page.map(event -> toDTO(event, emailById));
    }

    /**
     * Resumen por categoría: una sola query de agregación agrupando por
     * tabla_afectada. Devuelve una lista con un elemento por cada categoría
     * que tenga al menos 1 evento en la bitácora.
     */
    @Transactional(readOnly = true)
    /**
     * Handles summary.
     *
     * @return list of summary category audit dto matching the requested criteria
     */
    public List<SummaryCategoryAuditDTO> summary() {
        OffsetDateTime fromToday = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<Object[]> rows = auditLogAuditRepo.summaryByCategory(fromToday);
        List<SummaryCategoryAuditDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            String tableAfectada = (String) row[0];
            long totalEvents = (Long) row[1];
            long eventsToday = (Long) row[2];
            OffsetDateTime lastEvent = row[3] instanceof OffsetDateTime odt ? odt : null;

            // TODO: definir criterio de "Revisar" cuando el equipo lo defina
            boolean requiereReview = false;
            if ("sesiones".equals(tableAfectada)) {
                long failsRecientes = auditLogAuditRepo.contarLoginFailRecientes(
                        OffsetDateTime.now().minusHours(24));
                requiereReview = failsRecientes >= UMBRAL_LOGIN_FAIL_REVISAR;
            }

            result.add(new SummaryCategoryAuditDTO(
                    tableAfectada, totalEvents, eventsToday, lastEvent, requiereReview));
        }

        return result;
    }

    /**
     * Handles exportar CSV payload.
     *
     * @param userId numeric identifier used to scope this exportar CSV payload
     * @param module text value used to scope this exportar CSV payload
     * @param from date-time bound used to scope this exportar CSV payload
     * @param until date-time bound used to scope this exportar CSV payload
     * @return binary content of the generated file
     */

    public byte[] exportarCsv(Long userId, String module, OffsetDateTime from, OffsetDateTime until) {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10000, org.springframework.data.domain.Sort.by("fecha_hora").descending());
        var page = auditLogAuditRepo.searchWithFilters(userId, module, from, until, pageable);
        StringBuilder sb = new StringBuilder();
        sb.append("id,usuarioId,tipoOperacion,tablaAfectada,fechaHora,detalles\n");
        for (var e : page.getContent()) {
            sb.append(e.getId()).append(",").append(e.getUserId()).append(",").append(escape(e.getTypeOperacion())).append(",").append(escape(e.getTableAfectada())).append(",").append(e.getDateTime()).append(",").append(escape(e.getDetalles())).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private String escape(String s) { if (s==null) return ""; String t=s.replace("\"","\"\""); if (t.contains(",")||t.contains("\n")||t.contains("\"")) return "\""+t+"\""; return t; }

    private EventAuditResponseDTO toDTO(AuditLogAudit event, Map<Long, String> emailById) {
        String email = event.getUserId() == null ? null : emailById.get(event.getUserId());
        return new EventAuditResponseDTO(
                event.getId(),
                email,
                event.getTypeOperacion(),
                event.getDateTime(),
                event.getTableAfectada(),
                event.getDetalles());
    }
}
