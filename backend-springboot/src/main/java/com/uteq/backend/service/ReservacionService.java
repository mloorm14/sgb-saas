package com.uteq.backend.service;

import com.uteq.backend.dto.CambioEstadoReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionHoyResponseDTO;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

// La auditoria de esta tabla ya no se hace aqui: trg_auditoria_reservaciones
// (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
@Service
public class ReservacionService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String RESERVACION_NO_ENCONTRADA = "Reservación no encontrada: ";
    private static final String ESTADO_INICIAL = "PENDIENTE";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String CATALOGO_SIN_FILA = "Catálogo estados_reservacion sin fila '";

    private final ReservacionRepository reservacionRepo;
    private final EstadoReservacionRepository estadoReservacionRepo;
    private final UsuarioRepository usuarioRepo;
    private final ConfiguracionSistemaService configuracionSistemaService;

    public ReservacionService(ReservacionRepository reservacionRepo,
                              EstadoReservacionRepository estadoReservacionRepo,
                              UsuarioRepository usuarioRepo,
                              ConfiguracionSistemaService configuracionSistemaService) {
        this.reservacionRepo = reservacionRepo;
        this.estadoReservacionRepo = estadoReservacionRepo;
        this.usuarioRepo = usuarioRepo;
        this.configuracionSistemaService = configuracionSistemaService;
    }

    /**
     * Crea una reservación en estado PENDIENTE con fecha límite de retiro.
     * Valida que un LECTOR solo reserve para sí mismo, que no supere el
     * máximo de reservas activas y que no esté bloqueado por multas.
     *
     * @param dto datos de la reserva (usuario, libro y fecha de retiro opcional)
     * @param authentication autenticación vigente, usada para el control por rol
     * @return la reservación creada con su fecha límite calculada
     * @throws AuthorizationDeniedException si un LECTOR reserva para otro usuario
     * @throws IllegalStateException si supera el máximo de activas o está bloqueado por multa
     * @throws IllegalArgumentException si la fecha de retiro es anterior a hoy
     */
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
          try { max = configuracionSistemaService.obtenerValorEntero("max_reservas_por_usuario"); } catch (Exception ignored) {
              // best-effort: se usa el valor por defecto si falla la config
          }
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

        ZoneId zone = ZoneId.of("America/Guayaquil");
        OffsetDateTime ahora = OffsetDateTime.now(zone);

        Reservacion r = new Reservacion();
        r.setUsuarioId(dto.usuarioId());
        r.setLibroId(dto.libroId());
        r.setEstadoReservacionId(estadoInicial.getId());
        r.setFechaReserva(ahora);

        // Fecha limite: usa hora_limite_retiro_reserva (ej 18:00) del dia elegido
        String horaLimiteStr = "18:00";
          try { String v = configuracionSistemaService.obtenerValor("hora_limite_retiro_reserva"); if (v != null && !v.isBlank()) horaLimiteStr = v.trim(); } catch (Exception ignored) {
              // best-effort: se usa la hora por defecto si falla la config
          }
        LocalTime horaLimite = LocalTime.parse(horaLimiteStr.length()==5?horaLimiteStr+":00":horaLimiteStr);
        
        if (dto.fechaRetiro() != null) {
            OffsetDateTime fechaRetiroLocal = dto.fechaRetiro().withOffsetSameInstant(zone.getRules().getOffset(ahora.toInstant()));
            if (fechaRetiroLocal.toLocalDate().isBefore(ahora.toLocalDate())) {
                throw new IllegalArgumentException(
                        "La fecha de retiro no puede ser anterior a la fecha actual.");
            }
            OffsetDateTime limite = fechaRetiroLocal.withHour(horaLimite.getHour()).withMinute(horaLimite.getMinute()).withSecond(0).withNano(0);
            r.setFechaLimiteRetiro(limite);
        } else {
            OffsetDateTime limite = ahora.withHour(horaLimite.getHour()).withMinute(horaLimite.getMinute()).withSecond(0).withNano(0);
            if (limite.isBefore(ahora)) limite = limite.plusDays(1);
            r.setFechaLimiteRetiro(limite);
        }

        return r;
    }

    /**
     * Cambia el estado de una reservación pendiente (aceptar, rechazar o,
     * para el propio LECTOR, cancelar). Solo se admite transición desde
     * PENDIENTE: RETIRADA/EXPIRADA pertenecen al flujo de entrega y
     * vencimiento, no a este endpoint.
     *
     * @param reservacionId identificador de la reservación a transicionar
     * @param dto nuevo estado destino solicitado
     * @param authentication autenticación vigente, usada para el control por rol
     * @return la reservación con el estado actualizado
     * @throws EntityNotFoundException si la reservación no existe
     * @throws AuthorizationDeniedException si un LECTOR toca reserva ajena o pide otro estado que CANCELADA
     * @throws IllegalStateException si la reserva ya no está pendiente o el estado destino no existe en el catálogo
     */
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
                        CATALOGO_SIN_FILA + ESTADO_INICIAL + "'"));
        if (!estadoInicial.getId().equals(reservacion.getEstadoReservacionId())) {
            throw new IllegalStateException(
                    "Solo se puede aceptar o rechazar una reservación pendiente.");
        }

        EstadoReservacion estadoDestino = estadoReservacionRepo.findByNombre(dto.nuevoEstado())
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + dto.nuevoEstado() + "'"));

        reservacion.setEstadoReservacionId(estadoDestino.getId());
        reservacionRepo.save(reservacion);

        return toDTO(reservacion);
    }

    /**
     * Lista paginada de reservaciones de un usuario, con el mismo control
     * "propio vs cualquiera" que préstamos y multas: LECTOR solo las suyas.
     *
     * @param usuarioId identificador del dueño de las reservaciones
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados
     * @return página de reservaciones del usuario
     * @throws AuthorizationDeniedException si un LECTOR pide reservaciones ajenas
     */
    @Transactional(readOnly = true)
    public Page<ReservacionResponseDTO> listarPorUsuario(
            Long usuarioId, Authentication authentication, Pageable pageable) {
        validarAccesoUsuario(usuarioId, authentication);
        return reservacionRepo.findByUsuarioId(usuarioId, pageable).map(this::toDTO);
    }

    /**
     * Devuelve las reservaciones que vencen hoy, para la tarjeta operativa
     * del panel bibliotecario (retiros pendientes del día).
     *
     * @return lista de reservaciones del día con lector, libro y estado
     */
    @Transactional(readOnly = true)
    public List<ReservacionHoyResponseDTO> buscarReservacionesDeHoy() {
        return reservacionRepo.buscarReservacionesDeHoy().stream()
                .map(p -> new ReservacionHoyResponseDTO(
                        p.getReservacionId(),
                        p.getUsuarioNombre(),
                        p.getUsuarioCorreo(),
                        p.getLibroTitulo(),
                        p.getLibroIsbn(),
                        p.getEstadoNombre(),
                        p.getFechaLimiteRetiro() != null ? p.getFechaLimiteRetiro().atOffset(java.time.ZoneOffset.UTC) : null))
                .toList();
    }

    /**
     * Devuelve las reservaciones próximas a vencer, para anticipar retiros
     * y avisar antes de que expiren.
     *
     * @return lista de reservaciones próximas con lector, libro y estado
     */
    @Transactional(readOnly = true)
    public List<ReservacionHoyResponseDTO> buscarReservacionesProximas() {
        return reservacionRepo.buscarReservacionesProximas().stream()
                .map(p -> new ReservacionHoyResponseDTO(
                        p.getReservacionId(),
                        p.getUsuarioNombre(),
                        p.getUsuarioCorreo(),
                        p.getLibroTitulo(),
                        p.getLibroIsbn(),
                        p.getEstadoNombre(),
                        p.getFechaLimiteRetiro() != null ? p.getFechaLimiteRetiro().atOffset(java.time.ZoneOffset.UTC) : null))
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