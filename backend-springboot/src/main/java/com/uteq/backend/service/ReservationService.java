package com.uteq.backend.service;

import com.uteq.backend.dto.ChangeStatusReservationRequestDTO;
import com.uteq.backend.dto.ReservationTodayResponseDTO;
import com.uteq.backend.dto.ReservationRequestDTO;
import com.uteq.backend.dto.ReservationResponseDTO;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
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
public class ReservationService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String RESERVACION_NO_ENCONTRADA = "Reservación no encontrada: ";
    private static final String ESTADO_INICIAL = "PENDIENTE";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String CATALOGO_SIN_FILA = "Catálogo estados_reservacion sin fila '";

    private final ReservationRepository reservationRepo;
    private final StatusReservationRepository statusReservationRepo;
    private final UserRepository userRepo;
    private final ConfigurationSystemService configurationSystemService;

    public ReservationService(ReservationRepository reservationRepo,
                              StatusReservationRepository statusReservationRepo,
                              UserRepository userRepo,
                              ConfigurationSystemService configurationSystemService) {
        this.reservationRepo = reservationRepo;
        this.statusReservationRepo = statusReservationRepo;
        this.userRepo = userRepo;
        this.configurationSystemService = configurationSystemService;
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
    /**
     * Creates reservation Response data transfer object.
     *
     * @param dto reservation Request data transfer object used to scope this reservation Response data transfer object
     * @param authentication authentication of the caller used to scope this reservation Response data transfer object
     * @return reservation Response data transfer object reflecting the state after the operation
     * @throws AuthorizationDeniedException when the reservation Response data transfer object cannot be processed with the given input
     */
    public ReservationResponseDTO create(ReservationRequestDTO dto, Authentication authentication) {
        if (esReader(authentication)) {
            Long idOwn = resolveIdByEmail(authentication.getName());
            if (!idOwn.equals(dto.userId())) {
                throw new AuthorizationDeniedException(
                        "Un LECTOR solo puede reservar para sí mismo.");
            }
        }
        validateLimitReservations(dto.userId());
        validateDebts(dto.userId());
        return toDTO(reservationRepo.save(fromDTO(dto)));
    }

    private void validateLimitReservations(Long userId) {
        int max = 3;
          try { max = configurationSystemService.getValueEntero("max_reservas_por_usuario"); } catch (Exception ignored) {
              // best-effort: se usa el valor por defecto si falla la config
          }
        long actives = reservationRepo.countByUserIdAndStatusReservationIdIn(userId, List.of(1, 2));
        if (actives >= max) {
            throw new IllegalStateException("Has alcanzado el máximo de " + max + " reservas activas. Cancela o retira una para reservar otra.");
        }
    }

    private void validateDebts(Long userId) {
        User u = userRepo.findById(userId).orElse(null);
        if (u != null && u.getStatus() != null && "BLOQUEADO_POR_MULTA".equals(u.getStatus().getName())) {
            throw new IllegalStateException("Tienes multas pendientes. Regulariza tu situacion para poder reservar.");
        }
    }

    private Reservation fromDTO(ReservationRequestDTO dto) {
        // Se usa EstadoReservacionInicialNoConfiguradoException: si falta la fila
        // PENDIENTE en estados_reservacion es un problema de seed/configuración
        // del sistema, no un error del cliente -- mismo criterio que
        // LibroService.eliminar() con el catálogo estados_libro.
        StatusReservation statusInitial = statusReservationRepo.findByName(ESTADO_INICIAL)
                .orElseThrow(() -> new StatusReservationInitialNotConfiguredException(
                        "Catálogo estados_reservacion sin fila '" + ESTADO_INICIAL + "'"));

        ZoneId zone = ZoneId.of("America/Guayaquil");
        OffsetDateTime ahora = OffsetDateTime.now(zone);

        Reservation r = new Reservation();
        r.setUserId(dto.userId());
        r.setBookId(dto.bookId());
        r.setStatusReservationId(statusInitial.getId());
        r.setDateReservation(ahora);

        // Fecha limite: usa hora_limite_retiro_reserva (ej 18:00) del dia elegido
        String timeLimitStr = "18:00";
          try { String v = configurationSystemService.getValue("hora_limite_retiro_reserva"); if (v != null && !v.isBlank()) timeLimitStr = v.trim(); } catch (Exception ignored) {
              // best-effort: se usa la hora por defecto si falla la config
          }
        LocalTime timeLimit = LocalTime.parse(timeLimitStr.length()==5?timeLimitStr+":00":timeLimitStr);
        
        if (dto.datePickup() != null) {
            OffsetDateTime datePickupLocal = dto.datePickup().withOffsetSameInstant(zone.getRules().getOffset(ahora.toInstant()));
            if (datePickupLocal.toLocalDate().isBefore(ahora.toLocalDate())) {
                throw new IllegalArgumentException(
                        "La fecha de retiro no puede ser anterior a la fecha actual.");
            }
            OffsetDateTime limit = datePickupLocal.withHour(timeLimit.getHour()).withMinute(timeLimit.getMinute()).withSecond(0).withNano(0);
            r.setDateLimitPickup(limit);
        } else {
            OffsetDateTime limit = ahora.withHour(timeLimit.getHour()).withMinute(timeLimit.getMinute()).withSecond(0).withNano(0);
            if (limit.isBefore(ahora)) limit = limit.plusDays(1);
            r.setDateLimitPickup(limit);
        }

        return r;
    }

    /**
     * Cambia el estado de una reservación pendiente (aceptar, rechazar o,
     * para el propio LECTOR, cancelar). Solo se admite transición desde
     * PENDIENTE: RETIRADA/EXPIRADA pertenecen al flujo de entrega y
     * vencimiento, no a este endpoint.
     *
     * @param reservationId identificador de la reservación a transicionar
     * @param dto nuevo estado destino solicitado
     * @param authentication autenticación vigente, usada para el control por rol
     * @return la reservación con el estado actualizado
     * @throws EntityNotFoundException si la reservación no existe
     * @throws AuthorizationDeniedException si un LECTOR toca reserva ajena o pide otro estado que CANCELADA
     * @throws IllegalStateException si la reserva ya no está pendiente o el estado destino no existe en el catálogo
     */
    @Transactional
    /**
     * Changes reservation Response data transfer object.
     *
     * @param reservationId numeric identifier used to scope this reservation Response data transfer object
     * @param dto Cambio status reservation Request data transfer object used to scope this reservation Response data transfer object
     * @param authentication authentication of the caller used to scope this reservation Response data transfer object
     * @return reservation Response data transfer object reflecting the state after the operation
     * @throws AuthorizationDeniedException when the reservation Response data transfer object cannot be processed with the given input
     * @throws IllegalStateException when the reservation Response data transfer object cannot be processed with the given input
     * @throws EntityNotFoundException when the reservation Response data transfer object cannot be processed with the given input
     * @throws EstadoReservacionInicialNoConfiguradoException when the reservation Response data transfer object cannot be processed with the given input
     */
    public ReservationResponseDTO changeStatus(
            Long reservationId, ChangeStatusReservationRequestDTO dto, Authentication authentication) {
        Reservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException(RESERVACION_NO_ENCONTRADA + reservationId));

        // LECTOR solo puede cancelar su propia reserva pendiente
        if (esReader(authentication)) {
            Long idOwn = resolveIdByEmail(authentication.getName());
            if (!idOwn.equals(reservation.getUserId())) {
                throw new AuthorizationDeniedException("Un LECTOR solo puede cancelar sus propias reservaciones.");
            }
            if (!"CANCELADA".equals(dto.freshStatus())) {
                throw new AuthorizationDeniedException("Un LECTOR solo puede cancelar su reservacion.");
            }
        }

        // Transición válida solo desde PENDIENTE (aceptar o rechazar). Las
        // demás transiciones ya no son decisión del staff: RETIRADA/EXPIRADA
        // pertenecen al flujo de entrega/vencimiento y CANCELADA de una
        // reservación ya aceptada no tiene endpoint (fuera del alcance del
        // RF-10, documentado en el resumen de la rama).
        StatusReservation statusInitial = statusReservationRepo.findByName(ESTADO_INICIAL)
                .orElseThrow(() -> new StatusReservationInitialNotConfiguredException(
                        CATALOGO_SIN_FILA + ESTADO_INICIAL + "'"));
        if (!statusInitial.getId().equals(reservation.getStatusReservationId())) {
            throw new IllegalStateException(
                    "Solo se puede aceptar o rechazar una reservación pendiente.");
        }

        StatusReservation statusDestination = statusReservationRepo.findByName(dto.freshStatus())
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + dto.freshStatus() + "'"));

        reservation.setStatusReservationId(statusDestination.getId());
        reservationRepo.save(reservation);

        return toDTO(reservation);
    }

    /**
     * Lista paginada de reservaciones de un usuario, con el mismo control
     * "propio vs cualquiera" que préstamos y multas: LECTOR solo las suyas.
     *
     * @param userId identificador del dueño de las reservaciones
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados
     * @return página de reservaciones del usuario
     * @throws AuthorizationDeniedException si un LECTOR pide reservaciones ajenas
     */
    @Transactional(readOnly = true)
    /**
     * Lists reservation Response DTO records.
     *
     * @param userId numeric identifier used to scope this reservation Response DTO records
     * @param authentication authentication of the caller used to scope this reservation Response DTO records
     * @param pageable pagination information used to scope this reservation Response DTO records
     * @return page of reservation Response data transfer object for the requested pagination
     */
    public Page<ReservationResponseDTO> listByUser(
            Long userId, Authentication authentication, Pageable pageable) {
        validateAccessUser(userId, authentication);
        return reservationRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    /**
     * Devuelve las reservaciones que vencen hoy, para la tarjeta operativa
     * del panel bibliotecario (retiros pendientes del día).
     *
     * @return lista de reservaciones del día con lector, libro y estado
     */
    @Transactional(readOnly = true)
    /**
     * Searches reservation Hoy Response DTO records.
     *
     * @return list of reservation Hoy Response data transfer object matching the requested criteria
     */
    public List<ReservationTodayResponseDTO> searchReservationsToday() {
        return reservationRepo.searchReservationsToday().stream()
                .map(p -> new ReservationTodayResponseDTO(
                        p.getReservationId(),
                        p.getUserName(),
                        p.getUserEmail(),
                        p.getBookTitle(),
                        p.getBookIsbn(),
                        p.getStatusName(),
                        p.getDateLimitPickup() != null ? p.getDateLimitPickup().atOffset(java.time.ZoneOffset.UTC) : null))
                .toList();
    }

    /**
     * Devuelve las reservaciones próximas a vencer, para anticipar retiros
     * y avisar antes de que expiren.
     *
     * @return lista de reservaciones próximas con lector, libro y estado
     */
    @Transactional(readOnly = true)
    /**
     * Searches reservation Hoy Response DTO records.
     *
     * @return list of reservation Hoy Response data transfer object matching the requested criteria
     */
    public List<ReservationTodayResponseDTO> searchReservationsNexts() {
        return reservationRepo.searchReservationsNexts().stream()
                .map(p -> new ReservationTodayResponseDTO(
                        p.getReservationId(),
                        p.getUserName(),
                        p.getUserEmail(),
                        p.getBookTitle(),
                        p.getBookIsbn(),
                        p.getStatusName(),
                        p.getDateLimitPickup() != null ? p.getDateLimitPickup().atOffset(java.time.ZoneOffset.UTC) : null))
                .toList();
    }

    // ── "Propio vs cualquiera", mismo patrón que PrestamoService. ──
    private void validateAccessUser(Long userIdSolicitado, Authentication authentication) {
        if (!esReader(authentication)) {
            return;
        }
        Long idOwn = resolveIdByEmail(authentication.getName());
        if (!idOwn.equals(userIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propias reservaciones.");
        }
    }

    private boolean esReader(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + ROL_LECTOR));
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        return user.getId();
    }

    private ReservationResponseDTO toDTO(Reservation r) {
        return new ReservationResponseDTO(
                r.getId(),
                r.getUserId(),
                r.getBookId(),
                r.getStatusReservationId(),
                r.getDateReservation(),
                r.getDateLimitPickup());
    }
}