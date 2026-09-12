package com.uteq.backend.service;

import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.TypeNotification;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.NotificationRepository;
import com.uteq.backend.repository.TypeNotificationRepository;
import com.uteq.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepo;
    @Mock TypeNotificationRepository typeNotificationRepo;
    @Mock UserRepository userRepo;
    @Mock BookRepository bookRepo;
    @Mock EmailService emailService;

    @InjectMocks NotificationService notificationService;

    // ── Test 1: alerta de vencimiento -- la notificacion in-app se crea
    // igual; el correo automatico esta desactivado desde 2026-08-30 (ver
    // NotificacionService.crearYEnviar, lineas 195-201: en produccion
    // generaba 1k+ intentos de envio por corrida con "Authentication
    // failed" en SMTP -- solo el correo manual de verificacion de cuenta
    // sigue activo). ──
    @Test
    void generateAlertaDue_loanWithoutAlertaPrevia_creaNotificationWithoutSendEmail() {
        given(typeNotificationRepo.findByName("VENCIMIENTO"))
                .willReturn(Optional.of(typeWithId(1, "VENCIMIENTO")));
        given(notificationRepo.existsByLoanIdAndTypeNotificationId(50L, 1)).willReturn(false);
        given(userRepo.findById(1L)).willReturn(Optional.of(userWithEmail(1L, "lector@correo.com")));
        given(bookRepo.findById(2L)).willReturn(Optional.of(bookWithTitle("Clean Code")));

        notificationService.generateAlertaDue(loanWithId(50L));

        verify(notificationRepo).save(any());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    // ── Test 2: dedup -- ya existe una alerta VENCIMIENTO para este préstamo ──
    @Test
    void generateAlertaDue_yaNotificadoAntes_notReenviaNiDuplica() {
        given(typeNotificationRepo.findByName("VENCIMIENTO"))
                .willReturn(Optional.of(typeWithId(1, "VENCIMIENTO")));
        given(notificationRepo.existsByLoanIdAndTypeNotificationId(50L, 1)).willReturn(true);

        notificationService.generateAlertaDue(loanWithId(50L));

        verify(notificationRepo, never()).save(any());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    // ── Test 3: el registro queda con enviadoOk=false porque el correo
    // automatico esta desactivado (ver comentario de Test 1) -- ya no es un
    // fallo simulado de SMTP, es el comportamiento esperado hoy. ──
    @Test
    void notifyFine_fallaEnvio_igualGuardaRegistrationWithEnviadoOkFalse() {
        given(typeNotificationRepo.findByName("MULTA")).willReturn(Optional.of(typeWithId(2, "MULTA")));
        given(userRepo.findById(1L)).willReturn(Optional.of(userWithEmail(1L, "lector@correo.com")));

        notificationService.notifyFine(1L, 50L, new BigDecimal("2.50"));

        ArgumentCaptor<com.uteq.backend.entity.Notification> captor =
                ArgumentCaptor.forClass(com.uteq.backend.entity.Notification.class);
        verify(notificationRepo).save(captor.capture());
        assertThat(captor.getValue().isEnviadoOk()).isFalse();
        assertThat(captor.getValue().getErrorEnvio()).isNotNull();
    }

    // ── Test 4: reserva caducada -- prestamoId debe quedar null (no hay préstamo de origen) ──
    @Test
    void notifyReservationExpired_guardaWithLoanIdNulo() {
        given(typeNotificationRepo.findByName("RESERVA_CADUCADA"))
                .willReturn(Optional.of(typeWithId(3, "RESERVA_CADUCADA")));
        given(userRepo.findById(1L)).willReturn(Optional.of(userWithEmail(1L, "lector@correo.com")));
        given(bookRepo.findById(2L)).willReturn(Optional.of(bookWithTitle("Clean Code")));

        Reservation reservation = new Reservation();
        reservation.setId(70L);
        reservation.setUserId(1L);
        reservation.setBookId(2L);

        notificationService.notifyReservationExpired(reservation);

        ArgumentCaptor<com.uteq.backend.entity.Notification> captor =
                ArgumentCaptor.forClass(com.uteq.backend.entity.Notification.class);
        verify(notificationRepo).save(captor.capture());
        assertThat(captor.getValue().getLoanId()).isNull();
    }

    // ── Test 5: un LECTOR pidiendo el listado de OTRO usuario -> 403 ──
    @Test
    void listByUser_readerPideOtroUser_lanzaAccessDenegado() {
        Authentication auth = authComoRole("lector@correo.com", "LECTOR");
        given(userRepo.findByEmail("lector@correo.com"))
                .willReturn(Optional.of(userWithEmail(1L, "lector@correo.com")));

        assertThatThrownBy(() -> notificationService.listByUser(99L, auth, mock(Pageable.class)))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    // ── Test 6: un BIBLIOTECARIO puede consultar cualquier usuario ──
    @Test
    void listByUser_librarianPideCualquierUser_sePermite() {
        Authentication auth = authComoRole("biblio@correo.com", "BIBLIOTECARIO");
        given(notificationRepo.findByUserId(eq(99L), any())).willReturn(Page.empty());

        Page<?> result = notificationService.listByUser(99L, auth, mock(Pageable.class));

        assertThat(result).isEmpty();
    }

    private Authentication authComoRole(String email, String role) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .when(auth).getAuthorities();
        return auth;
    }

    private TypeNotification typeWithId(Integer id, String name) {
        TypeNotification type = new TypeNotification();
        type.setId(id);
        type.setName(name);
        return type;
    }

    private User userWithEmail(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private Book bookWithTitle(String title) {
        Book book = new Book();
        book.setTitle(title);
        return book;
    }

    private Loan loanWithId(Long id) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setUserId(1L);
        loan.setBookId(2L);
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().plusMinutes(10));
        return loan;
    }
}