package com.uteq.backend.service;

import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.EstadoReservacionRepository;
import com.uteq.backend.repository.ReservacionRepository;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ReservacionService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ESTADO_NO_ENCONTRADO = "Estado de reservación no encontrado: ";
    private static final String ESTADO_INICIAL = "PENDIENTE";
    private static final String ROL_LECTOR = "LECTOR";

    // Valor pendiente para el limite que pueda retirar un libro prestado
    // quedara con un día de limite para retirar un libro pedido.
    private static final int DIAS_LIMITE_RETIRO = 1;

    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final UsuarioRepository usuarioRepo;

    public ReservacionService(ReservacionRepository reservacionRepo,
                              EstadoReservacionRepository estadoReservacionRepo,
                              UsuarioRepository usuarioRepo) {
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Transactional
    public ReservacionResponseDTO crear(ReservacionRequestDTO dto, Authentication authentication) {
        if (esLector(authentication)) {
            Long idPropio = resolverIdPorCorreo(authentication.getName());
            if (!idPropio.equals(dto.usuarioId())) {
                throw new AuthorizationDeniedException(
                        "Un LECTOR solo puede reservar para sí mismo.");
            }
        }

        EstadoReservacion estadoInicial = estadoReservacionRepo.findByNombre(ESTADO_INICIAL)
                .orElseThrow(() -> new EntityNotFoundException(ESTADO_NO_ENCONTRADO + ESTADO_INICIAL));

        OffsetDateTime ahora = OffsetDateTime.now();

        Reservacion reservacion = new Reservacion();
        reservacion.setUsuarioId(dto.usuarioId());
        reservacion.setLibroId(dto.libroId());
        reservacion.setEstadoReservacionId(estadoInicial.getId());
        reservacion.setFechaReserva(ahora);
        reservacion.setFechaLimiteRetiro(ahora.plusDays(DIAS_LIMITE_RETIRO));

        Reservacion guardada = reservacionRepo.save(reservacion);
        return toDTO(guardada);
    }

    @Transactional(readOnly = true)
    public Page<ReservacionResponseDTO> listarPorUsuario(
            Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return reservacionRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    // ── "Propio vs cualquiera", mismo patrón que PrestamoService. ──
    private void validarAccesoUsuario(Long usuarioIdSolicitado, Authentication authentication) {
        if (!esLector(authentication)) {
            return;
        }
        Long idPropio = resolverIdPorCorreo(authentication.getName());
        if (!idPropio.equals(usuarioIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propias reservaciones.");
        }
    }

    private boolean esLector(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_" + ROL_LECTOR));
    }

    private Long resolverIdPorCorreo(String correo) {
        Usuario usuario = usuarioRepo.findByCorreo(correo)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + correo));
        return usuario.getId();
    }

    private ReservacionResponseDTO toDTO(Reservacion r) {
        return new ReservacionResponseDTO(
                r.getId(),
                r.getUsuarioId(),
                r.getLibroId(),
                r.getEstadoReservacionId(),
                r.getFechaReserva(),
                r.getFechaLimiteRetiro());
    }
}