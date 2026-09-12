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
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Consulta list owns usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return pagina de resultados que coincide con los filtros y la paginacion solicitada
     */
    public Page<SuggestionAcquisitionResponseDTO> listOwns(Authentication authentication, Pageable pageable) {
        Long userId = resolveIdByEmail(authentication.getName());
        return suggestionRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    // Solo GERENTE/ADMIN llegan acá: listado sin filtrar por dueño.
    @Transactional(readOnly = true)
    /**
     * Consulta list todas usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param status criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return pagina de resultados que coincide con los filtros y la paginacion solicitada
     */
    public Page<SuggestionAcquisitionResponseDTO> listAll(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return suggestionRepo.findAll(pageable).map(this::toDTO);
        }
        return suggestionRepo.findByStatus(status, pageable).map(this::toDTO);
    }

    @Transactional
    /**
     * Actualiza change status con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param freshStatus valor de entrada freshStatus usado por la operacion para completar su regla de negocio
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Consulta get most pedidos usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return pagina de resultados que coincide con los filtros y la paginacion solicitada
     */
    public Page<SuggestionGroupedDTO> getMostPedidos(Pageable pageable) {
        Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return suggestionRepo.findMostPedidosAgrupados(effective);
    }

    @Transactional(readOnly = true)
    /**
         * Busca/lista recursos.
     * @return lista o pagina de resultados
     */
    public List<SuggestionGroupedDTO> getMostPedidosList() {
        return suggestionRepo
                .findMostPedidosAgrupados(PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
    }

    /**
     * Procesa confirm acquisition y devuelve el resultado calculado por el backend.
     *
     * @param isbn valor de entrada isbn usado por la operacion para completar su regla de negocio
     * @param revisorId valor de entrada revisorId usado por la operacion para completar su regla de negocio
     * @return valor numerico calculado o recuperado por la operacion
     */
    @Transactional
    public int confirmAcquisition(String isbn, Long revisorId) {
        List<SuggestionAcquisition> pending = suggestionRepo.findByIsbnAndStatus(isbn, SuggestionAcquisition.PENDIENTE);
        for (SuggestionAcquisition s : pending) {
            s.setStatus(SuggestionAcquisition.APROBADA);
            s.setRevisadoBy(revisorId);
            suggestionRepo.save(s);
        }
        return pending.size();
    }

    private Long resolveIdByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email))
                .getId();
    }

    /** Versión pública para el controller (confirmar-adquisicion). */
    /**
     * Procesa resolve id by email public y devuelve el resultado calculado por el backend.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return valor numerico calculado o recuperado por la operacion
     */
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
