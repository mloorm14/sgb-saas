package com.uteq.backend.service;

import com.uteq.backend.dto.HistoryLoanDTO;
import com.uteq.backend.dto.ReservationActiveDTO;
import com.uteq.backend.dto.UserLoansManagementDTO;
import com.uteq.backend.dto.UserSuggestionDTO;
import com.uteq.backend.entity.Author;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Category;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.StatusFine;
import com.uteq.backend.entity.StatusLoan;
import com.uteq.backend.entity.StatusReservation;
import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.StatusFineRepository;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.repository.projection.FinePendingByLoanProjection;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoansManagementServiceTest {

    @Mock UserRepository userRepo;
    @Mock ReservationRepository reservationRepo;
    @Mock StatusReservationRepository statusReservationRepo;
    @Mock BookRepository bookRepo;
    @Mock LoanRepository loanRepo;
    @Mock StatusLoanRepository statusLoanRepo;
    @Mock FineRepository fineRepo;
    @Mock StatusFineRepository statusFineRepo;
    @Mock ConfigurationSystemService configurationSystemService;

    @Test
    void searchByEmail_cuandoExisteUsuario_retornaTarjetaOrdenandoRoles() {
        LoansManagementService service = service();
        User user = user(5L, "Ana", "Zamora", "ana@uteq.edu.ec", "ACTIVO", "LECTOR", "GERENTE");
        given(userRepo.findByEmail("ana@uteq.edu.ec")).willReturn(Optional.of(user));
        given(statusFineRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusFine(2)));
        given(fineRepo.sumAmountByUserIdAndStatusFineId(5L, 2)).willReturn(BigDecimal.valueOf(12));
        given(fineRepo.countByUserIdAndStatusFineId(5L, 2)).willReturn(3L);
        given(configurationSystemService.getValueEntero("dias_prestamo_default")).willReturn(7);

        UserLoansManagementDTO result = service.searchByEmail("ana@uteq.edu.ec");

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.nameFull()).isEqualTo("Ana Zamora");
        assertThat(result.typesUser()).containsExactly("GERENTE", "LECTOR");
        assertThat(result.amountFinesPendientes()).isEqualByComparingTo("12");
        assertThat(result.quantityFinesPendientes()).isEqualTo(3L);
        assertThat(result.daysLoanSuggested()).isEqualTo(7);
    }

    @Test
    void searchByEmail_cuandoNoExisteOLaFilaPendienteFalta_lanzaExcepcion() {
        LoansManagementService service = service();
        given(userRepo.findByEmail("no@uteq.edu.ec")).willReturn(Optional.empty());
        given(userRepo.findByEmail("ana@uteq.edu.ec"))
                .willReturn(Optional.of(user(5L, "Ana", "Zamora", "ana@uteq.edu.ec", "ACTIVO", "LECTOR")));
        given(statusFineRepo.findByName("PENDIENTE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchByEmail("no@uteq.edu.ec"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No se encontró");
        assertThatThrownBy(() -> service.searchByEmail("ana@uteq.edu.ec"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("estados_multa");
    }

    @Test
    void suggestionsUsers_validaLongitudYMapeaResultados() {
        LoansManagementService service = service();
        User user = user(6L, "Luis", "Mora", "luis@uteq.edu.ec", "ACTIVO", "LECTOR");
        given(userRepo.findTop3ByEmailContainingIgnoreCaseOrderByNameAsc("lu")).willReturn(List.of(user));

        assertThat(service.suggestionsUsers(null)).isEmpty();
        assertThat(service.suggestionsUsers(" l ")).isEmpty();
        List<UserSuggestionDTO> result = service.suggestionsUsers(" lu ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("luis@uteq.edu.ec");
        assertThat(result.get(0).statusAccount()).isEqualTo("ACTIVO");
    }

    @Test
    void reservationActive_cuandoReservaExiste_retornaLibroConAutoresCategoriasYPortada() {
        LoansManagementService service = service();
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusReservation(1)));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO")).willReturn(Optional.of(statusReservation(2)));
        Reservation reservation = reservation(20L, 30L);
        given(reservationRepo.findFirstByUserIdAndStatusReservationIdInOrderByDateReservationDesc(5L, List.of(1, 2)))
                .willReturn(Optional.of(reservation));
        given(bookRepo.findById(30L)).willReturn(Optional.of(book(30L, true)));
        given(configurationSystemService.getValueEntero("dias_prestamo_default")).willReturn(10);

        ReservationActiveDTO result = service.reservationActive(5L);

        assertThat(result.reservationId()).isEqualTo(20L);
        assertThat(result.bookId()).isEqualTo(30L);
        assertThat(result.authors()).containsExactly("A Autor", "Z Autor");
        assertThat(result.categories()).containsExactly("Novela");
        assertThat(result.tieneCover()).isTrue();
        assertThat(result.daysLoanSuggested()).isEqualTo(10);
    }

    @Test
    void reservationActive_cuandoNoHayReservaOLibro_lanza404() {
        LoansManagementService service = service();
        given(statusReservationRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusReservation(1)));
        given(statusReservationRepo.findByName("LISTA_PARA_RETIRO")).willReturn(Optional.of(statusReservation(2)));
        given(reservationRepo.findFirstByUserIdAndStatusReservationIdInOrderByDateReservationDesc(5L, List.of(1, 2)))
                .willReturn(Optional.empty(), Optional.of(reservation(20L, 30L)));
        given(bookRepo.findById(30L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservationActive(5L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("reservas vigentes");
        assertThatThrownBy(() -> service.reservationActive(5L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("libro");
    }

    @Test
    void history_cuandoNoHayPrestamos_retornaListaVacia() {
        LoansManagementService service = service();
        given(loanRepo.findByUserIdOrderByIdDesc(5L)).willReturn(List.of());

        assertThat(service.history(5L)).isEmpty();
    }

    @Test
    void history_cuandoHayPrestamos_resuelveCatalogosYFallbacks() {
        LoansManagementService service = service();
        Loan loanWithBook = loan(1L, 30L, 4);
        Loan loanWithoutBook = loan(2L, 99L, 5);
        given(loanRepo.findByUserIdOrderByIdDesc(5L)).willReturn(List.of(loanWithBook, loanWithoutBook));
        given(bookRepo.findAllById(anyList())).willReturn(List.of(book(30L, false)));
        StatusLoan status = new StatusLoan();
        status.setId(4);
        status.setName("DEVUELTO");
        given(statusLoanRepo.findAllById(anyList())).willReturn(List.of(status));
        given(statusFineRepo.findByName("PENDIENTE")).willReturn(Optional.of(statusFine(2)));
        given(fineRepo.findPendientesGroupedsByLoan(5L, 2)).willReturn(List.of(pendingFine(1L, BigDecimal.valueOf(8))));
        given(userRepo.findById(5L)).willReturn(Optional.of(user(5L, "Ana", "Zamora", "ana@uteq.edu.ec", "ACTIVO", "LECTOR")));

        List<HistoryLoanDTO> result = service.history(5L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).bookTitle()).isEqualTo("Clean Code");
        assertThat(result.get(0).finePending()).isTrue();
        assertThat(result.get(0).amountFinePending()).isEqualByComparingTo("8");
        assertThat(result.get(0).userName()).isEqualTo("Ana Zamora");
        assertThat(result.get(1).bookTitle()).isEqualTo("Libro #99");
        assertThat(result.get(1).bookIsbn()).isEmpty();
        assertThat(result.get(1).statusName()).isEmpty();
        assertThat(result.get(1).finePending()).isFalse();
    }

    private LoansManagementService service() {
        return new LoansManagementService(userRepo, reservationRepo, statusReservationRepo, bookRepo,
                loanRepo, statusLoanRepo, fineRepo, statusFineRepo, configurationSystemService);
    }

    private User user(Long id, String name, String lastName, String email, String statusName, String... roleNames) {
        StatusUser status = new StatusUser();
        status.setName(statusName);
        Set<Role> roles = new java.util.HashSet<>();
        for (String roleName : roleNames) {
            Role role = new Role();
            role.setName(roleName);
            roles.add(role);
        }
        return User.builder()
                .id(id)
                .name(name)
                .lastName(lastName)
                .email(email)
                .identificacionUser("123")
                .status(status)
                .roles(roles)
                .build();
    }

    private StatusFine statusFine(Integer id) {
        StatusFine status = new StatusFine();
        status.setId(id);
        status.setName("PENDIENTE");
        return status;
    }

    private StatusReservation statusReservation(Integer id) {
        StatusReservation status = new StatusReservation();
        status.setId(id);
        status.setName(id == 1 ? "PENDIENTE" : "LISTA_PARA_RETIRO");
        return status;
    }

    private Reservation reservation(Long id, Long bookId) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setBookId(bookId);
        reservation.setDateReservation(OffsetDateTime.now().minusDays(1));
        reservation.setDateLimitPickup(OffsetDateTime.now().plusDays(1));
        return reservation;
    }

    private Book book(Long id, boolean withCover) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("Clean Code");
        book.setIsbn("9780132350884");
        book.setYearPublication((short) 2008);
        book.setStockAvailable((short) 2);
        book.setStockTotal((short) 3);
        book.setLocationPhysical("A1");
        if (withCover) {
            book.setCoverUrl("https://example.test/cover.jpg");
        }
        Author z = new Author();
        z.setName("Z Autor");
        Author a = new Author();
        a.setName("A Autor");
        Category category = new Category();
        category.setName("Novela");
        book.getAuthors().add(z);
        book.getAuthors().add(a);
        book.getCategories().add(category);
        return book;
    }

    private Loan loan(Long id, Long bookId, Integer statusLoanId) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setBookId(bookId);
        loan.setStatusLoanId(statusLoanId);
        loan.setDateLoan(OffsetDateTime.now().minusDays(5));
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().plusDays(2));
        return loan;
    }

    private FinePendingByLoanProjection pendingFine(Long loanId, BigDecimal total) {
        return new FinePendingByLoanProjection() {
            @Override
            public Long getLoanId() {
                return loanId;
            }

            @Override
            public BigDecimal getTotalPending() {
                return total;
            }
        };
    }
}
