package com.uteq.backend.service;

import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
import com.uteq.backend.dto.SugerenciaAgrupadaDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.SugerenciaAdquisicion;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.SugerenciaAdquisicionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

// crear() resuelve el usuarioId desde el Authentication; cambiarEstado() registra quién revisó.
@Service
public class SugerenciaAdquisicionService {

    private static final String SUGERENCIA_NO_ENCONTRADA = "Sugerencia de adquisición no encontrada con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado con correo: ";
    private static final String TABLA_SUGERENCIAS = "sugerencias_adquisicion";

    private final SugerenciaAdquisicionRepository sugerenciaRepo;
    private final UsuarioRepository usuarioRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;

    public SugerenciaAdquisicionService(SugerenciaAdquisicionRepository sugerenciaRepo,
                                         UsuarioRepository usuarioRepo,
                                         BitacoraAuditoriaRepository bitacoraAuditoriaRepo) {
        this.sugerenciaRepo = sugerenciaRepo;
        this.usuarioRepo = usuarioRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
    }

    @Transactional
    public SugerenciaAdquisicionResponseDTO crear(SugerenciaAdquisicionRequestDTO dto, Authentication authentication) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());

        SugerenciaAdquisicion sugerencia = new SugerenciaAdquisicion();
        sugerencia.setUsuarioId(usuarioId);
        sugerencia.setTitulo(dto.titulo());
        sugerencia.setAutor(dto.autor());
        // ISBN opcional: "" se guarda como null para no chocar con el @Pattern del DTO.
        String isbn = dto.isbn() == null || dto.isbn().isBlank() ? null : dto.isbn();
        sugerencia.setIsbn(isbn);
        sugerencia.setJustificacion(dto.justificacion());
        sugerencia.setEstado(SugerenciaAdquisicion.PENDIENTE);

        return toDTO(sugerenciaRepo.save(sugerencia));
    }

    @Transactional(readOnly = true)
    public Page<SugerenciaAdquisicionResponseDTO> listarPropias(Authentication authentication, Pageable pageable) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        return sugerenciaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    // Solo GERENTE/ADMIN llegan acá: listado sin filtrar por dueño.
    @Transactional(readOnly = true)
    public Page<SugerenciaAdquisicionResponseDTO> listarTodas(String estado, Pageable pageable) {
        if (estado == null || estado.isBlank()) {
            return sugerenciaRepo.findAll(pageable).map(this::toDTO);
        }
        return sugerenciaRepo.findByEstado(estado, pageable).map(this::toDTO);
    }

    @Transactional
    public SugerenciaAdquisicionResponseDTO cambiarEstado(Long id, String nuevoEstado, Authentication authentication) {
        SugerenciaAdquisicion sugerencia = sugerenciaRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(SUGERENCIA_NO_ENCONTRADA + id));

        Long revisorId = resolverIdPorCorreo(authentication.getName());
        sugerencia.setEstado(nuevoEstado);
        sugerencia.setRevisadoPor(revisorId);

        SugerenciaAdquisicionResponseDTO resultado = toDTO(sugerenciaRepo.save(sugerencia));
        registrarAuditoria(revisorId, id, "Cambio de estado de sugerencia " + id + " a " + nuevoEstado);
        return resultado;
    }

    // ── Gestión por demanda: lo más pedido primero ──
    // El orden vive en el JPQL; el sort del Pageable se ignora a propósito.
    @Transactional(readOnly = true)
    public Page<SugerenciaAgrupadaDTO> getMasPedidos(Pageable pageable) {
        Pageable efectivo = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return sugerenciaRepo.findMasPedidosAgrupados(efectivo);
    }

    @Transactional(readOnly = true)
    public List<SugerenciaAgrupadaDTO> getMasPedidosList() {
        return sugerenciaRepo
                .findMasPedidosAgrupados(PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
    }

    /**
     * Confirma la adquisición de un ISBN: todas sus sugerencias PENDIENTE
     * pasan a APROBADA (que acá significa "adquirido") y salen del agrupado.
     * La llama el botón de gestión, el reporte no la usa, y LibroService al
     * crear un libro con ese ISBN (validación automática).
     */
    @Transactional
    public int confirmarAdquisicion(String isbn, Long revisorId) {
        List<SugerenciaAdquisicion> pendientes = sugerenciaRepo.findByIsbnAndEstado(isbn, SugerenciaAdquisicion.PENDIENTE);
        for (SugerenciaAdquisicion s : pendientes) {
            s.setEstado(SugerenciaAdquisicion.APROBADA);
            s.setRevisadoPor(revisorId);
            sugerenciaRepo.save(s);
            registrarAuditoria(revisorId, s.getId(),
                    "Sugerencia " + s.getId() + " confirmada por adquisición del ISBN " + isbn);
        }
        return pendientes.size();
    }

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_SUGERENCIAS)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }

    private Long resolverIdPorCorreo(String correo) {
        return usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo))
                .getId();
    }

    /** Versión pública para el controller (confirmar-adquisicion). */
    public Long resolverIdPorCorreoPublico(String correo) {
        return resolverIdPorCorreo(correo);
    }

    private SugerenciaAdquisicionResponseDTO toDTO(SugerenciaAdquisicion s) {
        return new SugerenciaAdquisicionResponseDTO(
                s.getId(),
                s.getUsuarioId(),
                s.getTitulo(),
                s.getAutor(),
                s.getIsbn(),
                s.getJustificacion(),
                s.getEstado(),
                s.getRevisadoPor(),
                s.getCreadoEn()
        );
    }
}
