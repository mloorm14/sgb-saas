package com.uteq.backend.service;

import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.entity.Multa;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.MultaProcedureRepository;
import com.uteq.backend.repository.MultaRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.repository.projection.ResumenFinancieroMultasProjection;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class MultaService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";

    // Únicos roles que sp_anular_multa acepta como p_rol_ejecutor (ver
    // db/procs/sp_anular_multa.sql: "IF p_rol_ejecutor <> 'GERENTE' AND
    // p_rol_ejecutor <> 'ADMIN' THEN RAISE ... LB422"). El @PreAuthorize
    // del controller ya restringe el endpoint a estos 2 roles, pero se
    // revalida acá como defensa en profundidad y para resolver CUÁL de
    // los dos usar si el usuario tuviera ambos.
    private static final Set<String> ROLES_ANULACION = Set.of("GERENTE", "ADMIN");

    private final MultaRepository multaRepo;
    private final MultaProcedureRepository multaProcRepo;
    private final UsuarioRepository usuarioRepo;

    public MultaService(MultaRepository multaRepo,
                        MultaProcedureRepository multaProcRepo,
                        UsuarioRepository usuarioRepo) {
        this.multaRepo = multaRepo;
        this.multaProcRepo = multaProcRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional(readOnly = true)
    public Page<MultaResponseDTO> listarPorUsuario(Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return multaRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional
    public MultaAccionResponseDTO pagar(Long multaId) {
        Map<String, Object> resultado = multaProcRepo.spPagarMulta(multaId);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    @Transactional
    public MultaAccionResponseDTO anular(Long multaId, String motivo, Authentication authentication) {
        String rolEjecutor = resolverRolAnulacion(authentication);
        Map<String, Object> resultado = multaProcRepo.spAnularMulta(multaId, motivo, rolEjecutor);
        return new MultaAccionResponseDTO(
                (Long) resultado.get("o_multa_id"),
                (Boolean) resultado.get("o_usuario_desbloqueado"));
    }

    // ── p_rol_ejecutor SIEMPRE desde las authorities reales del token,
    // nunca desde el body del request (ver Javadoc de AnulacionMultaRequestDTO). ──
    private String resolverRolAnulacion(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(rol -> rol.startsWith("ROLE_"))
                .map(rol -> rol.substring("ROLE_".length()))
                .filter(ROLES_ANULACION::contains)
                .findFirst()
                .orElseThrow(() -> new AuthorizationDeniedException(
                        "Solo GERENTE o ADMIN puede anular multas."));
    }

    // ── "Propio vs cualquiera", mismo patrón que PrestamoService/ReservacionService. ──
    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        boolean esLector = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_" + ROL_LECTOR));
        if (!esLector) {
            return;
        }
        Long idPropio = resolverIdPorCorreo(authentication.getName());
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propias multas.");
        }
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }

    // ── GET /reportes/resumen-financiero (dashboard GERENTE/ADMIN) ──
    @Transactional(readOnly = true)
    public ResumenFinancieroMultasResponseDTO reporteResumenFinanciero(OffsetDateTime desde, OffsetDateTime hasta) {
        ResumenFinancieroMultasProjection p = multaProcRepo.fnReporteResumenFinanciero(desde, hasta);
        return new ResumenFinancieroMultasResponseDTO(p.getTotalRecaudado(), p.getTotalPendiente());
    }

    private MultaResponseDTO toDTO(Multa m) {
        return new MultaResponseDTO(
                m.getId(),
                m.getPrestamoId(),
                m.getMonto(),
                m.getEstadoMultaId(),
                m.getFechaGenerada(),
                m.getFechaPagada(),
                m.getObservaciones());
    }
}