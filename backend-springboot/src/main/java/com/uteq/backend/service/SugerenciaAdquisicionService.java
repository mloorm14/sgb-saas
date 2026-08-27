package com.uteq.backend.service;

import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.SugerenciaAdquisicion;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.SugerenciaAdquisicionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

// Módulo 9.3 del roadmap. crear() resuelve el usuarioId siempre desde el
// Authentication autenticado (mismo criterio que FavoritoService): un
// LECTOR no puede sugerir a nombre de otro usuario. cambiarEstado() no
// valida rol acá -- eso vive en @PreAuthorize del controller -- pero sí
// registra quién revisó (revisadoPor), tomado también del Authentication,
// nunca de un campo que venga en el body.
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
        sugerencia.setIsbn(dto.isbn());
        sugerencia.setJustificacion(dto.justificacion());
        sugerencia.setEstado(SugerenciaAdquisicion.PENDIENTE);

        return toDTO(sugerenciaRepo.save(sugerencia));
    }

    @Transactional(readOnly = true)
    public Page<SugerenciaAdquisicionResponseDTO> listarPropias(Authentication authentication, Pageable pageable) {
        Long usuarioId = resolverIdPorCorreo(authentication.getName());
        return sugerenciaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    // Solo GERENTE/ADMIN llegan acá (ver @PreAuthorize en
    // SugerenciaAdquisicionController) -- listado sin filtrar por dueño,
    // a diferencia de listarPropias().
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

    private void registrarAuditoria(Long ejecutorId, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
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
