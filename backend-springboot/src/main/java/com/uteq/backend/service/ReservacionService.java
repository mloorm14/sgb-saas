package com.uteq.backend.service;

import com.uteq.backend.dto.CambioEstadoReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionHoyResponseDTO;
import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.EstadoReservacion;
import com.uteq.backend.entity.Reservacion;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
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

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReservacionService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String RESERVACION_NO_ENCONTRADA = "Reservación no encontrada: ";
    private static final String ESTADO_INICIAL = "PENDIENTE";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String TABLA_RESERVACIONES = "reservaciones";

    // Valor pendiente para el limite que pueda retirar un libro prestado
    // quedara con un día de limite para retirar un libro pedido.
    private static final int DIAS_LIMITE_RETIRO = 1;

    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final UsuarioRepository usuarioRepo;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public ReservacionService(ReservacionRepository reservacionRepo,
                              EstadoReservacionRepository estadoReservacionRepo,
                              UsuarioRepository usuarioRepo,
                              BitacoraAuditoriaRepository bitacoraAuditoriaRepo,
                              ConfiguracionSistemaService configuracionSistemaService) {
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.usuarioRepo = usuarioRepo;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
        this.configuracionSistemaService = configuracionSistemaService;
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
        validarLimiteReservas(dto.usuarioId());
        validarDeudas(dto.usuarioId());
        return toDTO(reservacionRepo.save(fromDTO(dto)));
    }

    private void validarLimiteReservas(Long usuarioId) {
        int max = 3;
        try { max = configuracionSistemaService.obtenerValorEntero("max_reservas_por_usuario"); } catch (Exception ignored) {}
        long activas = reservacionRepo.countByUsuarioIdAndEstadoReservacionIdIn(usuarioId, List.of(1, 2));
        if (activas >= max) {
            throw new IllegalStateException("Has alcanzado el máximo de " + max + " reservas activas. Cancela o retira una para reservar otra.");
        }
    }

    private void validarDeudas(Long usuarioId) {
        Usuario u = usuarioRepo.findById(usuarioId).orElse(null);
        if (u != null && u.getEstado() != null && "BLOQUEADO_POR_MULTA".equals(u.getEstado().getNombre())) {
            throw new IllegalStateException("Tienes multas pendientes. Regulariza tu situacion para poder reservar.");
        }
    }

    private Reservacion fromDTO(ReservacionRequestDTO dto) {
        // Se usa EstadoReservacionInicialNoConfiguradoException: si falta la fila
        // PENDIENTE en estados_reservacion es un problema de seed/configuración
        // del sistema, no un error del cliente -- mismo criterio que
        // LibroService.eliminar() con el catálogo estados_libro.
        EstadoReservacion estadoInicial = estadoReservacionRepo.findByNombre(ESTADO_INICIAL)
                .orElseThrow(() -> new EstadoReservacionInicialNoConfiguradoException(
                        "Catálogo estados_reservacion sin fila '" + ESTADO_INICIAL + "'"));

        OffsetDateTime ahora = OffsetDateTime.now();

        Reservacion r = new Reservacion();
        r.setUsuarioId(dto.usuarioId());
        r.setLibroId(dto.libroId());
        r.setEstadoReservacionId(estadoInicial.getId());
        r.setFechaReserva(ahora);

        // Fecha limite: usa hora_limite_retiro_reserva (ej 18:00) del dia elegido
        String horaLimiteStr = "18:00";
        try { String v = configuracionSistemaService.obtenerValor("hora_limite_retiro_reserva"); if (v != null && !v.isBlank()) horaLimiteStr = v.trim(); } catch (Exception ignored) {}
        LocalTime horaLimite = LocalTime.parse(horaLimiteStr.length()==5?horaLimiteStr+":00":horaLimiteStr);
        if (dto.fechaRetiro() != null) {
            if (dto.fechaRetiro().isBefore(ahora)) {
                throw new IllegalArgumentException(
                        "La fecha de retiro no puede ser anterior a la fecha actual.");
            }
            OffsetDateTime limite = dto.fechaRetiro().withHour(horaLimite.getHour()).withMinute(horaLimite.getMinute()).withSecond(0).withNano(0);
            r.setFechaLimiteRetiro(limite);
        } else {
            OffsetDateTime limite = ahora.withHour(horaLimite.getHour()).withMinute(horaLimite.getMinute()).withSecond(0).withNano(0);
            if (limite.isBefore(ahora)) limite = limite.plusDays(1);
            r.setFechaLimiteRetiro(limite);
        }

        return r;
    }

    @Transactional
    public ReservacionResponseDTO cambiarEstado(
            Long reservacionId, CambioEstadoReservacionRequestDTO dto, Authentication authentication) {
        Reservacion reservacion = reservacionRepo.findById(reservacionId)
                .orElseThrow(() -> new EntityNotFoundException(RESERVACION_NO_ENCONTRADA + reservacionId));

        // LECTOR solo puede cancelar su propia reserva pendiente
        if (esLector(authentication)) {
            Long idPropio = resolverIdPorCorreo(authentication.getName());
            if (!idPropio.equals(reservacion.getUsuarioId())) {
                throw new AuthorizationDeniedException("Un LECTOR solo puede cancelar sus propias reservaciones.");
            }
            if (!"CANCELADA".equals(dto.nuevoEstado())) {
                throw new AuthorizationDeniedException("Un LECTOR solo puede cancelar su reservacion.");
            }
        }

        // Transición válida solo desde PENDIENTE (aceptar o rechazar). Las
        // demás transiciones ya no son decisión del staff: RETIRADA/EXPIRADA
        // pertenecen al flujo de entrega/vencimiento y CANCELADA de una
        // reservación ya aceptada no tiene endpoint (fuera del alcance del
        // RF-10, documentado en el resumen de la rama).
        EstadoReservacion estadoInicial = estadoReservacionRepo.findByNombre(ESTADO_INICIAL)
                .orElseThrow(() -> new EstadoReservacionInicialNoConfiguradoException(
                        "Catálogo estados_reservacion sin fila '" + ESTADO_INICIAL + "'"));
        if (!estadoInicial.getId().equals(reservacion.getEstadoReservacionId())) {
            throw new IllegalStateException(
                    "Solo se puede aceptar o rechazar una reservación pendiente.");
        }

        EstadoReservacion estadoDestino = estadoReservacionRepo.findByNombre(dto.nuevoEstado())
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + dto.nuevoEstado() + "'"));

        reservacion.setEstadoReservacionId(estadoDestino.getId());
        reservacionRepo.save(reservacion);

        Long ejecutorId = resolverIdPorCorreo(authentication.getName());
        String accion = "LISTA_PARA_RETIRO".equals(dto.nuevoEstado()) ? "Aceptación" : "Rechazo";
        registrarAuditoria(ejecutorId, reservacion.getId(),
                accion + " de la reservación " + reservacion.getId()
                        + " del usuario " + reservacion.getUsuarioId()
                        + " para el libro " + reservacion.getLibroId()
                        + " (" + ESTADO_INICIAL + " -> " + dto.nuevoEstado() + ")");

        return toDTO(reservacion);
    }

    // Mismo patrón que UsuarioAdminService.registrarAuditoria(): el ejecutor
    // se resuelve desde el JWT autenticado (nunca del body) y el registro
    // afectado va en registroId para distinguir "quién hizo qué a quién".
    private void registrarAuditoria(Long ejecutorId, Long reservacionAfectadaId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(ejecutorId)
                .tipoOperacion("UPDATE")
                .tablaAfectada(TABLA_RESERVACIONES)
                .registroId(reservacionAfectadaId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }

    @Transactional(readOnly = true)
    public Page<ReservacionResponseDTO> listarPorUsuario(
            Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return reservacionRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<ReservacionHoyResponseDTO> buscarReservacionesDeHoy() {
        return reservacionRepo.buscarReservacionesDeHoy().stream()
                .map(p -> new ReservacionHoyResponseDTO(
                        p.getReservacionId(),
                        p.getUsuarioNombre(),
                        p.getUsuarioCorreo(),
                        p.getLibroTitulo(),
                        p.getEstadoNombre(),
                        p.getFechaLimiteRetiro()))
                .toList();
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