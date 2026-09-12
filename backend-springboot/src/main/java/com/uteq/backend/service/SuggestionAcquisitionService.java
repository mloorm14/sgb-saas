package com.uteq.backend.service;

import com.uteq.backend.dto.SuggestionAcquisitionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionResponseDTO;
import com.uteq.backend.dto.SuggestionGroupedDTO;
import com.uteq.backend.entity.SuggestionAcquisition;
import com.uteq.backend.repository.SuggestionAcquisitionRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// crear() resuelve el usuarioId desde el Authentication; cambiarEstado() registra quién revisó.
// La auditoria de esta tabla ya no se hace aqui: trg_auditoria_sugerencias_adquisicion
// (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
@Service
public class SuggestionAcquisitionService {

    private static final String SUGERENCIA_NO_ENCONTRADA = "Sugerencia de adquisición no encontrada con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado con correo: ";

    private final SuggestionAcquisitionRepository suggestionRepo;
    private final UserRepository userRepo;

    public SuggestionAcquisitionService(SuggestionAcquisitionRepository suggestionRepo,
                                         UserRepository userRepo) {
        this.suggestionRepo = suggestionRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    /**
     * Creates suggestion Adquisicion Response data transfer object.
     *
     * @param dto suggestion Adquisicion Request data transfer object used to scope this suggestion Adquisicion Response data transfer object
     * @param authentication authentication of the caller used to scope this suggestion Adquisicion Response data transfer object
     * @return suggestion Adquisicion Response data transfer object reflecting the state after the operation
     */
    public SuggestionAcquisitionResponseDTO create(SuggestionAcquisitionRequestDTO dto, Authentication authentication) {
        Long userId = resolveIdByEmail(authentication.getName());

        SuggestionAcquisition suggestion = new SuggestionAcquisition();
        suggestion.setUserId(userId);
        suggestion.setTitle(dto.title());
        suggestion.setAuthor(dto.author());
        // ISBN opcional: "" se guarda como null para no chocar con el @Pattern del DTO.
        String isbn = dto.isbn() == null || dto.isbn().isBlank() ? null : dto.isbn();
        suggestion.setIsbn(isbn);
        suggestion.setJustificacion(dto.justificacion());
        suggestion.setStatus(SuggestionAcquisition.PENDIENTE);

        return toDTO(suggestionRepo.save(suggestion));
    }

    @Transactional(readOnly = true)
    /**
     * Lists suggestion Adquisicion Response DTO records.
     *
     * @param authentication authentication of the caller used to scope this suggestion Adquisicion Response DTO records
     * @param pageable pagination information used to scope this suggestion Adquisicion Response DTO records
     * @return page of suggestion Adquisicion Response data transfer object for the requested pagination
     */
    public Page<SuggestionAcquisitionResponseDTO> listOwns(Authentication authentication, Pageable pageable) {
        Long userId = resolveIdByEmail(authentication.getName());
        return suggestionRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    // Solo GERENTE/ADMIN llegan acá: listado sin filtrar por dueño.
    @Transactional(readOnly = true)
    /**
     * Lists suggestion Adquisicion Response DTO records.
     *
     * @param estado text value used to scope this suggestion Adquisicion Response DTO records
     * @param pageable pagination information used to scope this suggestion Adquisicion Response DTO records
     * @return page of suggestion Adquisicion Response data transfer object for the requested pagination
     */
    public Page<SuggestionAcquisitionResponseDTO> listTodas(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return suggestionRepo.findAll(pageable).map(this::toDTO);
        }
        return suggestionRepo.findByStatus(status, pageable).map(this::toDTO);
    }

    @Transactional
    /**
     * Changes suggestion Adquisicion Response data transfer object.
     *
     * @param id numeric identifier used to scope this suggestion Adquisicion Response data transfer object
     * @param freshStatus text value used to scope this suggestion Adquisicion Response data transfer object
     * @param authentication authentication of the caller used to scope this suggestion Adquisicion Response data transfer object
     * @return suggestion Adquisicion Response data transfer object reflecting the state after the operation
     * @throws EntityNotFoundException when the suggestion Adquisicion Response data transfer object cannot be processed with the given input
     */
    public SuggestionAcquisitionResponseDTO changeStatus(Long id, String freshStatus, Authentication authentication) {
        SuggestionAcquisition suggestion = suggestionRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(SUGERENCIA_NO_ENCONTRADA + id));

        Long revisorId = resolveIdByEmail(authentication.getName());
        suggestion.setStatus(freshStatus);
        suggestion.setRevisadoBy(revisorId);

        return toDTO(suggestionRepo.save(suggestion));
    }

    // ── Gestión por demanda: lo más pedido primero ──
    // El orden vive en el JPQL; el sort del Pageable se ignora a propósito.
    @Transactional(readOnly = true)
    /**
     * Retrieves suggestion Agrupada DTO records.
     *
     * @param pageable pagination information used to scope this suggestion Agrupada DTO records
     * @return page of suggestion Agrupada data transfer object for the requested pagination
     */
    public Page<SuggestionGroupedDTO> getMostPedidos(Pageable pageable) {
        Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return suggestionRepo.findMostPedidosAgrupados(effective);
    }

    @Transactional(readOnly = true)
    /**
     * Retrieves suggestion Agrupada DTO records.
     *
     * @return list of suggestion Agrupada data transfer object matching the requested criteria
     */
    public List<SuggestionGroupedDTO> getMostPedidosList() {
        return suggestionRepo
                .findMostPedidosAgrupados(PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
    }

    /**
     * Confirma la adquisición de un ISBN: todas sus sugerencias PENDIENTE
     * pasan a APROBADA (que acá significa "adquirido") y salen del agrupado.
     * La llama el botón de gestión, el reporte no la usa, y LibroService al
     * crear un libro con ese ISBN (validación automática).
     */
    @Transactional
    /**
     * Confirms suggestion Adquisicion.
     *
     * @param isbn text value used to scope this suggestion Adquisicion
     * @param revisorId numeric identifier used to scope this suggestion Adquisicion
     * @return identifier of the affected record
     */
    public int confirmAcquisition(String isbn, Long revisorId) {
        List<SuggestionAcquisition> pendientes = suggestionRepo.findByIsbnAndStatus(isbn, SuggestionAcquisition.PENDIENTE);
        for (SuggestionAcquisition s : pendientes) {
            s.setStatus(SuggestionAcquisition.APROBADA);
            s.setRevisadoBy(revisorId);
            suggestionRepo.save(s);
        }
        return pendientes.size();
    }

    private Long resolveIdByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email))
                .getId();
    }

    /** Versión pública para el controller (confirmar-adquisicion). */
    public Long resolveIdByEmailPublic(String email) {
        return resolveIdByEmail(email);
    }

    private SuggestionAcquisitionResponseDTO toDTO(SuggestionAcquisition s) {
        return new SuggestionAcquisitionResponseDTO(
                s.getId(),
                s.getUserId(),
                s.getTitle(),
                s.getAuthor(),
                s.getIsbn(),
                s.getJustificacion(),
                s.getStatus(),
                s.getRevisadoBy(),
                s.getCreated()
        );
    }
}
