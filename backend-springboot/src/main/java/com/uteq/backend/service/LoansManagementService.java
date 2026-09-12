package com.uteq.backend.service;

import com.uteq.backend.dto.HistoryLoanDTO;
import com.uteq.backend.dto.ReservationActiveDTO;
import com.uteq.backend.dto.UserLoansManagementDTO;
import com.uteq.backend.dto.UserSuggestionDTO;
import com.uteq.backend.entity.Author;
import com.uteq.backend.entity.Category;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusFineRepository;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lecturas de la ventanilla de préstamos del bibliotecario (módulo
 * "Préstamos" del sidebar): encontrar al usuario por correo y armar todo lo
 * que la pantalla necesita en una pasada -- tarjeta de identificación,
 * reserva vigente (Caso A) e historial reciente.
 *
 * Reutiliza el modelo existente sin duplicar nada (ver Javadoc de los DTOs):
 * - correo           -> usuarios.correo (identidad de login, UNIQUE; misma
 *                       columna que resuelve findByCorreo en todo el sistema)
 * - cédula (informativa en la tarjeta) -> usuarios.identificacion_usuario
 * - tipo de usuario -> roles del usuario
 * - estado de cuenta-> estados_usuario.nombre
 * - multas pendientes -> agregado sobre multas x prestamos (no es columna)
 * - días de préstamo sugeridos -> configuracion_sistema 'dias_prestamo_default'
 *
 * Sobre el bloqueo del Caso C: la REGLA de que no se puede prestar a un
 * usuario con multas ya vive en sp_crear_prestamo (rechaza
 * BLOQUEADO_POR_MULTA) y el sistema mantiene la invariante "multas
 * pendientes &gt; 0 &lt;=&gt; usuario BLOQUEADO_POR_MULTA" de forma atómica
 * (sp_registrar_devolucion bloquea al generar la multa; sp_pagar_multa /
 * sp_anular_multa solo desbloquean cuando ya no queda ninguna PENDIENTE).
 * Acá solo se CALCULA el monto para pintar la alerta y el motivo exacto --
 * no se duplica la validación de creación, que sigue centralizada en el SP
 * que invoca PrestamoService.crear().
 */
@Service
public class LoansManagementService {

    private static final String USUARIO_NO_ENCONTRADO =
            "No se encontró ningún usuario con este correo";
    private static final String SIN_RESERVA_VIGENTE = "El usuario no tiene reservas vigentes";
    private static final String ESTADO_MULTA_PENDIENTE = "PENDIENTE";

    // "Vigente" = todavía puede terminar en retiro (mismo criterio que
    // PrestamoService.ESTADOS_RESERVA_VIGENTE; no existe un estado literal
    // "ACTIVA" en estados_reservacion).
    private static final List<String> ESTADOS_RESERVA_VIGENTE =
            List.of(ESTADO_MULTA_PENDIENTE, "LISTA_PARA_RETIRO");

    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";

    // Tope del historial reciente: línea de tiempo acotada, no un listado
    // paginado (para el historial completo ya existe GET /prestamos/usuario/{id}).
    private static final int LIMITE_HISTORIAL = 20;

    private final UserRepository userRepo;
    private final ReservationRepository reservationRepo;
    private final StatusReservationRepository statusReservationRepo;
    private final BookRepository bookRepo;
    private final LoanRepository loanRepo;
    private final StatusLoanRepository statusLoanRepo;
    private final FineRepository fineRepo;
    private final StatusFineRepository statusFineRepo;
    private final ConfigurationSystemService configurationSystemService;

    public LoansManagementService(UserRepository userRepo,
                                   ReservationRepository reservationRepo,
                                   StatusReservationRepository statusReservationRepo,
                                   BookRepository bookRepo,
                                   LoanRepository loanRepo,
                                   StatusLoanRepository statusLoanRepo,
                                   FineRepository fineRepo,
                                   StatusFineRepository statusFineRepo,
                                   ConfigurationSystemService configurationSystemService) {
        this.userRepo = userRepo;
        this.reservationRepo = reservationRepo;
        this.statusReservationRepo = statusReservationRepo;
        this.bookRepo = bookRepo;
        this.loanRepo = loanRepo;
        this.statusLoanRepo = statusLoanRepo;
        this.fineRepo = fineRepo;
        this.statusFineRepo = statusFineRepo;
        this.configurationSystemService = configurationSystemService;
    }

    // ── GET /gestion/buscar-usuario?correo= ──────────────────
    @Transactional(readOnly = true)
    /**
     * Consulta search by email usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public UserLoansManagementDTO searchByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO));

        Integer idFinePending = idStatusFine(ESTADO_MULTA_PENDIENTE);
        BigDecimal amountPending = fineRepo.sumAmountByUserIdAndStatusFineId(
                user.getId(), idFinePending);
        long quantityPendientes = fineRepo.countByUserIdAndStatusFineId(
                user.getId(), idFinePending);

        return new UserLoansManagementDTO(
                user.getId(),
                (user.getName() + " " + user.getLastName()).trim(),
                user.getIdentificacionUser(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .sorted()
                        .toList(),
                user.getStatus().getName(),
                amountPending,
                quantityPendientes,
                daysLoanSuggested());
    }

    // ── GET /gestion/sugerencias-usuarios?correo= ───────────
    // Autocompletado predictivo: retorna hasta 3 usuarios cuyo correo
    // contenga el texto ingresado (case-insensitive).
    @Transactional(readOnly = true)
    /**
     * Procesa suggestions users y devuelve el resultado calculado por el backend.
     *
     * @param email texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<UserSuggestionDTO> suggestionsUsers(String email) {
        if (email == null || email.trim().length() < 2) {
            return List.of();
        }
        return userRepo.findTop3ByEmailContainingIgnoreCaseOrderByNameAsc(email.trim())
                .stream()
                .map(u -> new UserSuggestionDTO(
                        u.getId(),
                        (u.getName() + " " + u.getLastName()).trim(),
                        u.getEmail(),
                        u.getStatus().getName()))
                .toList();
    }

    // ── GET /gestion/reserva-activa?usuarioId= ───────────────
    // 404 (EntityNotFoundException) si no hay reserva vigente: el frontend
    // interpreta ese 404 como "Caso B: préstamo directo".
    @Transactional(readOnly = true)
    /**
     * Procesa reservation active y devuelve el resultado calculado por el backend.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public ReservationActiveDTO reservationActive(Long userId) {
        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(this::idStatusReservation)
                .toList();

        Reservation reservation = reservationRepo
                .findFirstByUserIdAndStatusReservationIdInOrderByDateReservationDesc(userId, idsVigentes)
                .orElseThrow(() -> new EntityNotFoundException(SIN_RESERVA_VIGENTE));

        Book book = bookRepo.findById(reservation.getBookId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El libro de la reservación " + reservation.getId() + " no existe"));

        return new ReservationActiveDTO(
                reservation.getId(),
                book.getId(),
                book.getTitle(),
                book.getAuthors().stream()
                        .map(Author::getName)
                        .sorted()
                        .toList(),
                book.getIsbn(),
                reservation.getDateReservation(),
                reservation.getDateLimitPickup(),
                daysLoanSuggested(),
                book.getYearPublication(),
                book.getStockAvailable(),
                book.getStockTotal(),
                book.getLocationPhysical(),
                book.getCategories().stream()
                        .map(Category::getName)
                        .sorted()
                        .toList(),
                book.getCoverImage() != null || (book.getCoverUrl() != null && !book.getCoverUrl().isBlank()));
    }

    // ── GET /gestion/historial?usuarioId= ────────────────────
    // Tres consultas en total (préstamos, libros+estados por lote, multas
    // agrupadas), nunca una por fila. Lista vacía si el usuario no tiene
    // préstamos: el frontend muestra "Este usuario no tiene préstamos
    // registrados".
    @Transactional(readOnly = true)
    /**
     * Procesa history y devuelve el resultado calculado por el backend.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<HistoryLoanDTO> history(Long userId) {
        List<Loan> loans = loanRepo.findByUserIdOrderByIdDesc(userId);
        if (loans.isEmpty()) {
            return List.of();
        }
        if (loans.size() > LIMITE_HISTORIAL) {
            loans = loans.subList(0, LIMITE_HISTORIAL);
        }

        List<Book> books = bookRepo.findAllById(
                        loans.stream().map(Loan::getBookId).distinct().toList());
        Map<Long, String> titlesByBook = books.stream()
                .collect(Collectors.toMap(Book::getId, Book::getTitle));
        Map<Long, String> isbnByBook = books.stream()
                .collect(Collectors.toMap(Book::getId, l -> l.getIsbn() != null ? l.getIsbn() : ""));
        Map<Long, List<String>> authorsByBook = books.stream()
                .collect(Collectors.toMap(Book::getId,
                        l -> l.getAuthors().stream().map(a -> a.getName()).toList()));
        Map<Long, List<String>> categoriesByBook = books.stream()
                .collect(Collectors.toMap(Book::getId,
                        l -> l.getCategories().stream().map(c -> c.getName()).toList()));

        Map<Integer, String> nombresByStatus = statusLoanRepo.findAllById(
                        loans.stream().map(Loan::getStatusLoanId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getName()));

        Integer idFinePending = idStatusFine(ESTADO_MULTA_PENDIENTE);
        Map<Long, BigDecimal> pendientesByLoan = new HashMap<>();
        for (var row : fineRepo.findPendientesGroupedsByLoan(userId, idFinePending)) {
            pendientesByLoan.put(row.getLoanId(), row.getTotalPending());
        }

        User user = userRepo.findById(userId).orElse(null);
        String userName = user != null
                ? (user.getName() + " " + user.getLastName()).trim()
                : "";
        String userEmail = user != null ? user.getEmail() : "";

        return loans.stream()
                .map(p -> toHistoryDTO(p, titlesByBook, isbnByBook, authorsByBook, categoriesByBook, nombresByStatus, pendientesByLoan, userName, userEmail))
                .toList();
    }

    private HistoryLoanDTO toHistoryDTO(
            Loan p,
            Map<Long, String> titlesByBook,
            Map<Long, String> isbnByBook,
            Map<Long, List<String>> authorsByBook,
            Map<Long, List<String>> categoriesByBook,
            Map<Integer, String> nombresByStatus,
            Map<Long, BigDecimal> pendientesByLoan,
            String userName,
            String userEmail) {
        BigDecimal amountPending = pendientesByLoan.get(p.getId());
        return new HistoryLoanDTO(
                p.getId(),
                p.getBookId(),
                titlesByBook.getOrDefault(p.getBookId(), "Libro #" + p.getBookId()),
                isbnByBook.getOrDefault(p.getBookId(), ""),
                authorsByBook.getOrDefault(p.getBookId(), List.of()),
                categoriesByBook.getOrDefault(p.getBookId(), List.of()),
                p.getDateLoan(),
                p.getDateLoanReturnEstimada(),
                p.getDateLoanReturnReal(),
                nombresByStatus.getOrDefault(p.getStatusLoanId(), ""),
                amountPending != null,
                amountPending != null ? amountPending : BigDecimal.ZERO,
                userName,
                userEmail);
    }

    // Días de préstamo prellenados según la configuración del sistema
    // ('dias_prestamo_default', editable por el Admin en /admin/configuracion).
    private Integer daysLoanSuggested() {
        return configurationSystemService.getValueEntero(CLAVE_DIAS_PRESTAMO_DEFAULT);
    }

    // Se usa IllegalStateException para "fila de catálogo faltante": problema
    // de seed/configuración, no error del cliente (mismo criterio que
    // PrestamoService.idEstadoPrestamo).
    private Integer idStatusFine(String name) {
        return statusFineRepo.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_multa sin fila '" + name + "'"))
                .getId();
    }

    private Integer idStatusReservation(String name) {
        return statusReservationRepo.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_reservacion sin fila '" + name + "'"))
                .getId();
    }
}
