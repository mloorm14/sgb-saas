package com.uteq.backend.service;

import com.uteq.backend.dto.HistoryReservationDTO;
import com.uteq.backend.dto.UserReservationsManagementDTO;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lecturas de la ventanilla de reservaciones del bibliotecario (módulo
 * "Reservaciones" del sidebar): encontrar al usuario por correo y armar
 * la pantalla -- tarjeta de identificación, historial de reservaciones.
 *
 * La CREACIÓN de la reservación se mantiene en ReservacionService.crear()
 * (POST /api/v1/reservaciones), que ahora acepta fechaRetiro opcional.
 */
@Service
public class ReservationsManagementService {

    private static final String USUARIO_NO_ENCONTRADO =
            "No se encontró ningún usuario con este correo";
    private static final int LIMITE_RESERVAS_ACTIVAS = 3;

    private static final List<String> ESTADOS_RESERVA_VIGENTE =
            List.of("PENDIENTE", "LISTA_PARA_RETIRO");

    private final UserRepository userRepo;
    private final ReservationRepository reservationRepo;
    private final StatusReservationRepository statusReservationRepo;
    private final BookRepository bookRepo;

    public ReservationsManagementService(UserRepository userRepo,
                                       ReservationRepository reservationRepo,
                                       StatusReservationRepository statusReservationRepo,
                                       BookRepository bookRepo) {
        this.userRepo = userRepo;
        this.reservationRepo = reservationRepo;
        this.statusReservationRepo = statusReservationRepo;
        this.bookRepo = bookRepo;
    }

    // ── GET /gestion/buscar-usuario?correo= ──────────────────
    @Transactional(readOnly = true)
    /**
     * Consulta search by email usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public UserReservationsManagementDTO searchByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO));

        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(this::idStatusReservation)
                .toList();

        long quantityActives = reservationRepo.countByUserIdAndStatusReservationIdIn(
                user.getId(), idsVigentes);

        return new UserReservationsManagementDTO(
                user.getId(),
                (user.getName() + " " + user.getLastName()).trim(),
                user.getEmail(),
                user.getStatus().getName(),
                quantityActives,
                LIMITE_RESERVAS_ACTIVAS);
    }

    // ── GET /gestion/historial-reservaciones?usuarioId= ───────
    // Retorna las reservaciones del usuario con el título del libro
    // resuelto en batch (3 queries: reservaciones, libros, estados).
    @Transactional(readOnly = true)
    /**
     * Procesa history reservations y devuelve el resultado calculado por el backend.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<HistoryReservationDTO> historyReservations(Long userId) {
        // Validar que el usuario exista
        if (!userRepo.existsById(userId)) {
            throw new EntityNotFoundException("Usuario no encontrado: " + userId);
        }

        List<Reservation> reservations = reservationRepo
                .findByUserId(userId,
                        org.springframework.data.domain.PageRequest.of(0, 50,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "fechaReserva")))
                .getContent();

        if (reservations.isEmpty()) {
            return List.of();
        }

        Map<Long, String> titlesByBook = bookRepo.findAllById(
                        reservations.stream().map(Reservation::getBookId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        com.uteq.backend.entity.Book::getId,
                        com.uteq.backend.entity.Book::getTitle));

        Map<Integer, String> nombresByStatus = statusReservationRepo.findAllById(
                        reservations.stream().map(Reservation::getStatusReservationId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(StatusReservation::getId, StatusReservation::getName));

        return reservations.stream()
                .map(r -> new HistoryReservationDTO(
                        r.getId(),
                        titlesByBook.getOrDefault(r.getBookId(), "Libro #" + r.getBookId()),
                        nombresByStatus.getOrDefault(r.getStatusReservationId(), ""),
                        r.getStatusReservationId(),
                        r.getDateReservation(),
                        r.getDateLimitPickup()))
                .toList();
    }

    private Integer idStatusReservation(String name) {
        return statusReservationRepo.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + name + "'"))
                .getId();
    }
}
