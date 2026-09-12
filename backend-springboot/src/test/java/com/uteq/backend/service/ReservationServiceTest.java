package com.uteq.backend.service;

import com.uteq.backend.dto.ChangeStatusReservationRequestDTO;
import com.uteq.backend.dto.ReservationRequestDTO;
import com.uteq.backend.dto.ReservationResponseDTO;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepo;
    @Mock StatusReservationRepository statusReservationRepo;
    @Mock UserRepository userRepo;

    @InjectMocks ReservationService reservationService;

    // ── Test 1: creación exitosa (LECTOR reserva para sí mismo) ──
    @Test
    void create_readerReservationForSiMismo_creaReservationPending() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusWithId(1)));
        given(reservationRepo.save(any())).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        ReservationResponseDTO result = reservationService.create(
                new ReservationRequestDTO(1L, 3L, null), auth);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.bookId()).isEqualTo(3L);
        assertThat(result.statusReservationId()).isEqualTo(1);
        assertThat(result.dateLimitPickup()).isAfter(result.dateReservation());
    }

    // ── Test 1.1: creación con fechaRetiro válida (respeta el límite de hora) ──
    @Test
    void create_withDatePickupValida_asignaDateLimitCorrecta() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusWithId(1)));
        given(reservationRepo.save(any())).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(51L);
            return r;
        });

        // Simulamos una fecha de retiro para "hoy" pero al inicio del día con un offset específico
        java.time.ZoneId zone = java.time.ZoneId.of("America/Guayaquil");
        java.time.OffsetDateTime ahoraLocal = java.time.OffsetDateTime.now(zone);
        java.time.OffsetDateTime datePickup = ahoraLocal;

        ReservationResponseDTO result = reservationService.create(
                new ReservationRequestDTO(1L, 3L, datePickup), auth);

        assertThat(result.id()).isEqualTo(51L);
        assertThat(result.dateLimitPickup().getHour()).isEqualTo(18); // Por defecto es 18:00
    }

    // ── Test 2: LECTOR intenta reservar para OTRO usuario -> denegado ──
    @Test
    void create_readerReservationForOtroUser_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequestDTO(2L, 3L, null), auth))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 3: BIBLIOTECARIO reserva en nombre de otro usuario -> permitido ──
    @Test
    void create_librarianReservationForOtroUser_sePermite() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.of(statusWithId(1)));
        given(reservationRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        ReservationResponseDTO result = reservationService.create(
                new ReservationRequestDTO(2L, 3L, null), auth);

        assertThat(result.userId()).isEqualTo(2L);
    }

    // ── Test 4: catálogo PENDIENTE faltante -> error de sistema (500), no 404 ──
    @Test
    void create_withoutCatalogoPending_lanzaIllegalState() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(statusReservationRepo.findByName("PENDIENTE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(
                new ReservationRequestDTO(2L, 3L, null), auth))
                .isInstanceOf(StatusReservationInitialNotConfiguredException.class)
                .hasMessageContaining("PENDIENTE");
    }

    // ── Test 5: acceso denegado al listar reservaciones de otro usuario ──
    @Test
    void listByUser_cuandoReaderPideOtroUser_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithId(1L)));

        assertThatThrownBy(() -> reservationService.listByUser(2L, auth, null))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: aceptar una reservación pendiente -> LISTA_PARA_RETIRO ──
    @Test
    void changeStatus_aceptarPending_quedaListaForPickup() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(reservationRepo.findById(50L)).willReturn(Optional.of(reservationPending(50L)));
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusWithId(1)));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO")).willReturn(Optional.of(statusWithId(2)));

        ReservationResponseDTO result = reservationService.changeStatus(
                50L, new ChangeStatusReservationRequestDTO("LISTA_PARA_RETIRO"), auth);

        assertThat(result.id()).isEqualTo(50L);
        assertThat(result.statusReservationId()).isEqualTo(2);
        verify(reservationRepo).save(any(Reservation.class));
    }

    // ── Test 7: rechazar una reservación pendiente -> CANCELADA ──
    @Test
    void changeStatus_rechazarPending_quedaCancelada() {
        Authentication auth = authComoRole("gerente@correo.com", "GERENTE");
        given(reservationRepo.findById(51L)).willReturn(Optional.of(reservationPending(51L)));
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusWithId(1)));
        given(statusReservationRepo.findByName("CANCELADA")).willReturn(Optional.of(statusWithId(5)));

        ReservationResponseDTO result = reservationService.changeStatus(
                51L, new ChangeStatusReservationRequestDTO("CANCELADA"), auth);

        assertThat(result.statusReservationId()).isEqualTo(5);
    }

    // ── Test 8: reservación inexistente -> 404 ──
    @Test
    void changeStatus_reservationNotExiste_lanzaEntityNotFound() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(reservationRepo.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.changeStatus(
                999L, new ChangeStatusReservationRequestDTO("CANCELADA"), auth))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── Test 9: ya no está pendiente -> no se puede aceptar/rechazar ──
    @Test
    void changeStatus_reservationYaRetirada_lanzaIllegalState() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        Reservation retirada = reservationPending(52L);
        retirada.setStatusReservationId(3);
        given(reservationRepo.findById(52L)).willReturn(Optional.of(retirada));
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusWithId(1)));

        assertThatThrownBy(() -> reservationService.changeStatus(
                52L, new ChangeStatusReservationRequestDTO("LISTA_PARA_RETIRO"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pendiente");
    }

    // ── Test 10: catálogo destino faltante -> error de sistema (500) ──
    @Test
    void changeStatus_withoutCatalogoDestination_lanzaIllegalState() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(reservationRepo.findById(53L)).willReturn(Optional.of(reservationPending(53L)));
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusWithId(1)));
        given(statusReservationRepo.findByName("CANCELADA")).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.changeStatus(
                53L, new ChangeStatusReservationRequestDTO("CANCELADA"), auth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELADA");
    }

    // ── Helpers ────────────────────────────────────────────
    private Authentication authComoRole(String email, String role) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(auth).getAuthorities();
        return auth;
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private StatusReservation statusWithId(Integer id) {
        StatusReservation status = new StatusReservation();
        status.setId(id);
        return status;
    }

    private Reservation reservationPending(Long id) {
        Reservation r = new Reservation();
        r.setId(id);
        r.setUserId(1L);
        r.setBookId(3L);
        r.setStatusReservationId(1);
        return r;
    }
}