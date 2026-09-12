package com.uteq.backend.service;

import com.uteq.backend.dto.LoanReturnResponseDTO;
import com.uteq.backend.dto.BookMostLoanedDetailedResponseDTO;
import com.uteq.backend.dto.BookMostLoanedResponseDTO;
import com.uteq.backend.dto.LoanActiveResponseDTO;
import com.uteq.backend.dto.LoanRequestDTO;
import com.uteq.backend.dto.LoanResponseDTO;
import com.uteq.backend.dto.RenewalResponseDTO;
import com.uteq.backend.dto.ReportCategoriesDemandedResponseDTO;
import com.uteq.backend.dto.ReportInventoryResponseDTO;
import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.dto.ReportOverduesResponseDTO;
import com.uteq.backend.entity.StatusLoan;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.StatusLoanRepository;
import com.uteq.backend.repository.StatusReservationRepository;
import com.uteq.backend.repository.LoanProcedureRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.ReservationRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.repository.projection.BookMostLoanedDetailedProjection;
import com.uteq.backend.repository.projection.BookMostLoanedProjection;
import com.uteq.backend.repository.projection.LoanActiveProjection;
import com.uteq.backend.repository.projection.ReportCategoriesDemandedProjection;
import com.uteq.backend.repository.projection.ReportInventoryProjection;
import com.uteq.backend.repository.projection.ReportDelinquencyProjection;
import com.uteq.backend.repository.projection.ReportUsageByPeriodProjection;
import com.uteq.backend.repository.projection.ReportOverduesProjection;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

// La auditoria de prestamos ya no se hace aqui: trg_auditoria_prestamos
// (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
@Service
public class LoanService {

    private static final String PRESTAMO_NO_ENCONTRADO = "Préstamo no encontrado con id: ";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";

    // ── Constantes de renovar() ──
    private static final String ESTADO_DEVUELTO = "DEVUELTO";
    private static final String ESTADO_RENOVADO = "RENOVADO";
    // "Vigente": reserva que aún puede terminar en retiro (no RETIRADA/EXPIRADA/CANCELADA).
    private static final List<String> ESTADOS_RESERVA_VIGENTE = List.of("PENDIENTE", "LISTA_PARA_RETIRO");
    private static final String ESTADO_RESERVA_RETIRADA = "RETIRADA";
    private static final String CLAVE_DIAS_PRESTAMO_DEFAULT = "dias_prestamo_default";
    private static final String CLAVE_MAX_RENOVACIONES_DEFAULT = "max_renovaciones_default";
    private static final String CATALOGO_ESTADOS_RESERVA = "Catálogo estados_reservacion sin fila '";
    private static final String PRESTAMO_MSG = "El préstamo ";

    private final LoanRepository loanRepo;
    private final LoanProcedureRepository loanProcRepo;
    private final UserRepository userRepo;
    private final StatusLoanRepository statusLoanRepo;
    private final ReservationRepository reservationRepo;
    private final StatusReservationRepository statusReservationRepo;
    private final ConfigurationSystemService configurationSystemService;
    private final CredentialQrService credentialQrService;
    private final NotificationService notificationService;

    public LoanService(LoanRepository loanRepo,
                           LoanProcedureRepository loanProcRepo,
                           UserRepository userRepo,
                           StatusLoanRepository statusLoanRepo,
                           ReservationRepository reservationRepo,
                           StatusReservationRepository statusReservationRepo,
                           ConfigurationSystemService configurationSystemService,
                           CredentialQrService credentialQrService,
                           NotificationService notificationService) {
        this.loanRepo = loanRepo;
        this.loanProcRepo = loanProcRepo;
        this.userRepo = userRepo;
        this.statusLoanRepo = statusLoanRepo;
        this.reservationRepo = reservationRepo;
        this.statusReservationRepo = statusReservationRepo;
        this.configurationSystemService = configurationSystemService;
        this.credentialQrService = credentialQrService;
        this.notificationService = notificationService;
    }

    /**
     * Crea un préstamo delegando en {@code sp_crear_prestamo} (valida stock
     * y bloqueo por multas a nivel de motor). Resuelve al lector por
     * usuarioId directo o token QR de credencial, valida su tope de
     * préstamos activos y, si nace de una reserva vigente, la vincula y la
     * marca RETIRADA.
     *
     * @param dto datos del préstamo (lector por id o QR, libro, días y reserva opcional)
     * @param authentication autenticación del bibliotecario que registra, queda como responsable
     * @return el préstamo creado
     * @throws EntityNotFoundException si el préstamo creado no se puede releer
     * @throws IllegalArgumentException si la identificación del lector es ambigua o la reserva no corresponde
     * @throws IllegalStateException si la reserva ya no está vigente o se supera el tope de préstamos
     */
    @Transactional
    public LoanResponseDTO create(LoanRequestDTO dto, Authentication authentication) {
        Long userId = resolveUserId(dto);
        Long librarianId = resolveIdByEmail(authentication.getName());

        validateLimitLoans(userId);

        // Ventanilla: si nace de una reserva, se valida ANTES de tocar stock y se vincula DESPUÉS del SP.
        Reservation reservationSource = validateReservationSiAplica(dto, userId);
        Long loanId = loanProcRepo.spCreateLoan(
                userId, dto.bookId(), librarianId, dto.daysLoan());
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + loanId));
        if (reservationSource != null) {
            loan.setReservationId(reservationSource.getId());
            loanRepo.save(loan);
            reservationSource.setStatusReservationId(idStatusReservation(ESTADO_RESERVA_RETIRADA));
            reservationRepo.save(reservationSource);
        }
        return toDTO(loan);
    }

    // Valida que la reservacionId sea una reserva VIGENTE del mismo usuario y libro; o null si es directo.
    private Reservation validateReservationSiAplica(LoanRequestDTO dto, Long userId) {
        if (dto.reservationId() == null) {
            return null;
        }
        Reservation reservation = reservationRepo.findById(dto.reservationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Reservación no encontrada: " + dto.reservationId()));
        if (!userId.equals(reservation.getUserId())) {
            throw new IllegalArgumentException(
                    "La reservación " + dto.reservationId() + " no pertenece al usuario del préstamo.");
        }
        if (!dto.bookId().equals(reservation.getBookId())) {
            throw new IllegalArgumentException(
                    "El libro del préstamo no coincide con el de la reservación "
                            + dto.reservationId() + ".");
        }
        List<Integer> idsVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(name -> statusReservationRepo.findByName(name)
                        .orElseThrow(() -> new IllegalStateException(
                                CATALOGO_ESTADOS_RESERVA + name + "'"))
                        .getId())
                .toList();
        if (!idsVigentes.contains(reservation.getStatusReservationId())) {
            throw new IllegalStateException(
                    "La reservación " + dto.reservationId()
                            + " ya no está vigente (pendiente o lista para retiro).");
        }
        return reservation;
    }

    // Resuelve el usuario por credencialQrToken o usuarioId directo; debe venir EXACTAMENTE uno.
    private Long resolveUserId(LoanRequestDTO dto) {
        boolean tieneToken = dto.credentialQrToken() != null;
        boolean tieneUserId = dto.userId() != null;
        if (tieneToken == tieneUserId) {
            throw new IllegalArgumentException(
                    "Debe enviarse exactamente uno de: usuarioId o credencialQrToken.");
        }
        if (tieneToken) {
            return credentialQrService.resolveByToken(dto.credentialQrToken()).getId();
        }
        return dto.userId();
    }

    /**
     * Registra la devolución de un préstamo vía {@code sp_registrar_devolucion},
     * que calcula atraso y genera la multa si corresponde. Si hubo multa,
     * dispara la notificación al lector. Único punto donde nace una multa.
     *
     * @param loanId préstamo a devolver
     * @return resultado con el id, si hubo multa y su monto
     * @throws EntityNotFoundException si el préstamo no existe al notificar
     */
    @Transactional
    public LoanReturnResponseDTO registerLoanReturn(Long loanId) {
        Map<String, Object> result = loanProcRepo.spRegisterLoanReturn(loanId);
        Boolean huboFine = (Boolean) result.get("o_hubo_multa");
        BigDecimal amountFine = (BigDecimal) result.get("o_monto_multa");

        // Las multas se crean dentro de sp_registrar_devolucion: este es el único punto donde se sabe que hubo una.
        if (Boolean.TRUE.equals(huboFine)) {
            Loan loan = loanRepo.findById(loanId)
                    .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + loanId));
            notificationService.notifyFine(loan.getUserId(), loanId, amountFine);
        }

        return new LoanReturnResponseDTO(
                (Long) result.get("o_prestamo_id"), huboFine, amountFine);
    }

    // ── POST /{id}/renovacion ──
    // Validaciones en Java (consultas + UPDATE simple): cada rechazo lanza una excepción distinta.
    // Orden: 1. no existe → 404 / 2. LECTOR ajeno → 403 / 3. devuelto → 400
    //   4. vencido → 409 / 5. límite de renovaciones → 409 / 6. reserva vigente de otro → 409
    /**
     * Renueva un préstamo extendiendo su fecha estimada y marcándolo RENOVADO.
     * Valida en orden: existencia, acceso del LECTOR, no devuelto, no vencido,
     * tope de renovaciones y ausencia de reserva vigente de otro usuario.
     * Cada rechazo lanza una excepción distinta para mapear el HTTP correcto.
     *
     * @param loanId préstamo a renovar
     * @param authentication autenticación vigente, un LECTOR solo renueva lo suyo
     * @return renovación con nueva fecha, renovaciones usadas y restantes
     * @throws EntityNotFoundException si el préstamo no existe
     * @throws AuthorizationDeniedException si un LECTOR renueva préstamo ajeno
     * @throws IllegalArgumentException si ya fue devuelto
     * @throws LoanOverdueException si está vencido
     * @throws LimitRenewalsExceededException si agotó sus renovaciones
     * @throws MaterialReservadoException si otro usuario tiene reserva vigente del libro
     */
    @Transactional
    public RenewalResponseDTO renew(Long loanId, Authentication authentication) {
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + loanId));

        validateAccessUser(loan.getUserId(), authentication);

        if (ESTADO_DEVUELTO.equals(nameStatusLoan(loan.getStatusLoanId()))) {
            throw new IllegalArgumentException(
                    PRESTAMO_MSG + loanId + " ya fue devuelto, no se puede renovar.");
        }

        if (loan.getDateLoanReturnEstimada().isBefore(OffsetDateTime.now())) {
            throw new LoanOverdueException(
                    PRESTAMO_MSG + loanId + " está vencido, no se puede renovar.");
        }

        int maxRenewals = configurationSystemService.getValueEntero(CLAVE_MAX_RENOVACIONES_DEFAULT);
        if (loan.getRenewalsRealizadas() >= maxRenewals) {
            throw new LimitRenewalsExceededException(
                    "El préstamo " + loanId + " ya alcanzó el máximo de "
                            + maxRenewals + " renovaciones permitidas.");
        }

        if (existeReservationVigenteOtroUser(loan.getBookId(), loan.getUserId())) {
            throw new MaterialReservadoException(
                    "El libro del préstamo " + loanId
                            + " tiene una reserva vigente de otro usuario.");
        }

        int daysLoan = configurationSystemService.getValueEntero(CLAVE_DIAS_PRESTAMO_DEFAULT);
        loan.setDateLoanReturnEstimada(OffsetDateTime.now().plusDays(daysLoan));
        loan.setRenewalsRealizadas((short) (loan.getRenewalsRealizadas() + 1));
        loan.setStatusLoanId(idStatusLoan(ESTADO_RENOVADO));
        loanRepo.save(loan);

        return new RenewalResponseDTO(
                loan.getId(),
                loan.getDateLoanReturnEstimada(),
                loan.getRenewalsRealizadas(),
                (short) (maxRenewals - loan.getRenewalsRealizadas()));
    }

    private boolean existeReservationVigenteOtroUser(Long bookId, Long userIdDuenoLoan) {
        List<Integer> idsStatusesVigentes = ESTADOS_RESERVA_VIGENTE.stream()
                .map(name -> statusReservationRepo.findByName(name)
                        .orElseThrow(() -> new IllegalStateException(
                                CATALOGO_ESTADOS_RESERVA + name + "'"))
                        .getId())
                .toList();
        return reservationRepo.existsByBookIdAndStatusReservationIdInAndUserIdNot(
                bookId, idsStatusesVigentes, userIdDuenoLoan);
    }

    // Fila de catálogo faltante = problema de seed/configuración, no error del cliente.
    private String nameStatusLoan(Integer statusId) {
        return statusLoanRepo.findById(statusId)
                .map(StatusLoan::getName)
                .orElseThrow(() -> new IllegalStateException(
                        "estado_prestamo_id " + statusId + " no existe en el catálogo estados_prestamo"));
    }

    private Integer idStatusLoan(String name) {
        return statusLoanRepo.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_prestamo sin fila '" + name + "'"))
                .getId();
    }

    // Conversión de reserva en préstamo: la reserva origen queda RETIRADA.
    private Integer idStatusReservation(String name) {
        return statusReservationRepo.findByName(name)
                .orElseThrow(() -> new IllegalStateException(
                        CATALOGO_ESTADOS_RESERVA + name + "'"))
                .getId();
    }

    /**
     * Lista paginada de préstamos de un usuario. Un LECTOR solo ve los
     * suyos; otros roles ven los de cualquiera.
     *
     * @param userId dueño de los préstamos
     * @param authentication autenticación vigente para el control por rol
     * @param pageable paginación y orden
     * @return página de préstamos del usuario
     * @throws AuthorizationDeniedException si un LECTOR pide préstamos ajenos
     */
    @Transactional(readOnly = true)
    public Page<LoanResponseDTO> listByUser(Long userId, Authentication authentication, Pageable pageable) {
        validateAccessUser(userId, authentication);
        return loanRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    /**
     * Lista los préstamos activos de un usuario con días restantes, para la
     * vista "mis préstamos" del lector. Mismo control de acceso que el listado.
     *
     * @param userId dueño de los préstamos activos
     * @param authentication autenticación vigente para el control por rol
     * @return préstamos no devueltos del usuario con su estado
     * @throws AuthorizationDeniedException si un LECTOR pide préstamos ajenos
     */
    @Transactional(readOnly = true)
    public List<LoanActiveResponseDTO> listActivesByUser(Long userId, Authentication authentication) {
        validateAccessUser(userId, authentication);
        return loanRepo.findActivesByUserId(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    private static final int LIMITE_REPORTE_DEFAULT = 10;

    /**
     * Ranking de libros más prestados en un rango de fechas, para reportes
     * gerenciales. El límite nulo se normaliza a 10 en Java porque la
     * función SQL exige un límite explícito.
     *
     * @param limit tope de filas, 10 si es nulo
     * @param from inicio del rango, puede ser nulo (sin cota)
     * @param until fin del rango, puede ser nulo (sin cota)
     * @return libros ordenados por total de préstamos
     */
    @Transactional(readOnly = true)
    public List<BookMostLoanedResponseDTO> reportBooksMostLoaned(
            Integer limit, OffsetDateTime from, OffsetDateTime until) {
        // El default 10 se aplica en Java: la @Query siempre envía p_limite explícito y null daría LIMIT NULL.
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        return loanProcRepo.fnReportBooksMostLoaned(limitEffective, from, until).stream()
                .map(this::toDTO)
                .toList();
    }

    // ── GET /reportes/morosidad ──
    // Default 10 aplicado en Java por el mismo motivo que reporteLibrosMasPrestados.
    /**
     * Índice de morosidad por lector (deuda total, multas pendientes y atraso
     * promedio), para la gestión de cobranza. Límite nulo equivale a 10.
     *
     * @param limit tope de filas, 10 si es nulo
     * @return lectores morosos ordenados por deuda
     */
    @Transactional(readOnly = true)
    public List<ReportDelinquencyResponseDTO> reportDelinquency(Integer limit) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        return loanProcRepo.fnReportIndexDelinquency(limitEffective).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Versión paginada del índice de morosidad, para tablas grandes del
     * panel gerente. Pagina en base de datos con límite/offset del pageable.
     *
     * @param limit tope del ranking base, 10 si es nulo
     * @param pageable página y tamaño solicitados
     * @return página del ranking de morosidad con su total
     */
    @Transactional(readOnly = true)
    public Page<ReportDelinquencyResponseDTO> reportDelinquencyPaginated(Integer limit, Pageable pageable) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        int pageSize = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportDelinquencyProjection> projections = loanProcRepo.fnReportIndexDelinquencyPaginated(limitEffective, pageSize, offset);
        long total = loanProcRepo.countReportIndexDelinquency(limitEffective);
        List<ReportDelinquencyResponseDTO> content = projections.stream().map(this::toDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ── GET /reportes/uso ──
    // granularidad inválida → 400; el fallback en SQL es solo defensa en profundidad.
    private static final List<String> GRANULARIDADES_VALIDAS = List.of("dia", "semana", "mes");

    /**
     * Uso del servicio por período (préstamos vs devoluciones) con
     * granularidad día, semana o mes, para gráficos de tendencia.
     * Granularidad nula equivale a día; otra granularidad es error 400.
     *
     * @param granularidad agrupación temporal: dia, semana o mes
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @return serie temporal de uso
     * @throws IllegalArgumentException si la granularidad no es dia, semana ni mes
     */
    @Transactional(readOnly = true)
    public List<ReportUsageByPeriodResponseDTO> reportUsageByPeriod(
            String granularidad, OffsetDateTime from, OffsetDateTime until) {
        String granularidadEfectiva = (granularidad != null) ? granularidad.toLowerCase() : "dia";
        if (!GRANULARIDADES_VALIDAS.contains(granularidadEfectiva)) {
            throw new IllegalArgumentException(
                    "granularidad inválida: '" + granularidad
                            + "'. Valores permitidos: dia, semana, mes.");
        }
        return loanProcRepo.fnReportUsageByPeriod(granularidadEfectiva, from, until).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Versión paginada del uso por período, para series largas.
     *
     * @param granularidad agrupación temporal: dia, semana o mes
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @param pageable página y tamaño solicitados
     * @return página de la serie temporal con su total
     * @throws IllegalArgumentException si la granularidad no es dia, semana ni mes
     */
    @Transactional(readOnly = true)
    public Page<ReportUsageByPeriodResponseDTO> reportUsageByPeriodPaginated(
            String granularidad, OffsetDateTime from, OffsetDateTime until, Pageable pageable) {
        String granularidadEfectiva = (granularidad != null) ? granularidad.toLowerCase() : "dia";
        if (!GRANULARIDADES_VALIDAS.contains(granularidadEfectiva)) {
            throw new IllegalArgumentException("granularidad inválida: '" + granularidad + "'. Valores permitidos: dia, semana, mes.");
        }
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportUsageByPeriodProjection> projections = loanProcRepo.fnReportUsageByPeriodPaginated(granularidadEfectiva, from, until, limit, offset);
        long total = loanProcRepo.countReportUsageByPeriod(granularidadEfectiva, from, until);
        List<ReportUsageByPeriodResponseDTO> content = projections.stream().map(this::toDTO).toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ── Propio vs cualquiera ──
    // LECTOR solo su propio usuarioId; BIBLIOTECARIO/GERENTE sin restricción.
    private void validateAccessUser(Long userIdSolicitado, Authentication authentication) {
        boolean esReader = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + ROL_LECTOR));
        if (!esReader) {
            return;
        }
        Long idOwn = resolveIdByEmail(authentication.getName());
        if (!idOwn.equals(userIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propios préstamos.");
        }
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        return user.getId();
    }

    private LoanResponseDTO toDTO(Loan p) {
        return new LoanResponseDTO(
                p.getId(),
                p.getUserId(),
                p.getBookId(),
                p.getLibrarianId(),
                p.getReservationId(),
                p.getDateLoan(),
                p.getDateLoanReturnEstimada(),
                p.getDateLoanReturnReal(),
                p.getRenewalsRealizadas(),
                p.getStatusLoanId());
    }

    private LoanActiveResponseDTO toDTO(LoanActiveProjection p) {
        return new LoanActiveResponseDTO(
                p.getLoanId(),
                p.getBookTitle(),
                p.getBookIsbn(),
                p.getDateLoan() != null ? p.getDateLoan().atOffset(ZoneOffset.UTC) : null,
                p.getDateLoanReturnEstimada() != null ? p.getDateLoanReturnEstimada().atOffset(ZoneOffset.UTC) : null,
                p.getDaysRestantes(),
                p.getStatusName());
    }

    private BookMostLoanedResponseDTO toDTO(BookMostLoanedProjection p) {
        return new BookMostLoanedResponseDTO(
                p.getBookId(),
                p.getTitle(),
                p.getIsbn(),
                p.getTotalLoans());
    }

    /**
     * Ranking detallado de libros más prestados con autor, categoría y
     * porcentaje sobre el total, para el reporte gerencial completo.
     *
     * @param limit tope de filas, 10 si es nulo
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @param categoryId filtro opcional por categoría
     * @return detalle ordenado por total de préstamos
     */
    @Transactional(readOnly = true)
    public List<BookMostLoanedDetailedResponseDTO> reportBooksMostLoanedDetailed(
            Integer limit, OffsetDateTime from, OffsetDateTime until, Integer categoryId) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        return loanProcRepo.fnReportBooksMostLoanedDetailed(limitEffective, from, until, categoryId).stream()
                .map(p -> new BookMostLoanedDetailedResponseDTO(
                        p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(), p.getTotalLoans(), p.getPercentage()))
                .toList();
    }

    /**
     * Versión paginada del ranking detallado, para tablas grandes.
     *
     * @param limit tope del ranking base, 10 si es nulo
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @param categoryId filtro opcional por categoría
     * @param pageable página y tamaño solicitados
     * @return página del ranking detallado con su total
     */
    @Transactional(readOnly = true)
    public Page<BookMostLoanedDetailedResponseDTO> reportBooksMostLoanedDetailedPaginated(
            Integer limit, OffsetDateTime from, OffsetDateTime until, Integer categoryId, Pageable pageable) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        int pageSize = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<BookMostLoanedDetailedProjection> projections = loanProcRepo.fnReportBooksDetailedPaginated(limitEffective, from, until, categoryId, pageSize, offset);
        long total = loanProcRepo.countReportBooksDetailed(limitEffective, from, until, categoryId);
        List<BookMostLoanedDetailedResponseDTO> content = projections.stream()
                .map(p -> new BookMostLoanedDetailedResponseDTO(p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(), p.getTotalLoans(), p.getPercentage()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * Inventario con stock y disponibilidad por libro, con filtros básicos
     * de categoría, estado, texto y paginación simple. Los 11 filtros
     * gerenciales avanzados viajan nulos a la función.
     *
     * @param categoryId filtro opcional por categoría
     * @param statusStock filtro opcional por estado de stock
     * @param busqueda texto libre sobre título/isbn, opcional
     * @return inventario filtrado con stock total y disponible
     */
    @Transactional(readOnly = true)
    public List<ReportInventoryResponseDTO> reportInventory(
            Integer categoryId, String statusStock, String busqueda) {
        return loanProcRepo.fnReportInventory(categoryId, statusStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null).stream()
                .map(p -> new ReportInventoryResponseDTO(
                        p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(),
                        p.getStockTotal(), p.getStockAvailable(), p.getStatusAvailability(),
                        p.getPublisherName(), p.getSupplierName(), p.getLanguageName(), p.getStatusBookName(),
                        p.getYearPublication(), p.getLocationPhysical()))
                .toList();
    }

    /**
     * Versión paginada del inventario con filtros básicos.
     *
     * @param categoryId filtro opcional por categoría
     * @param statusStock filtro opcional por estado de stock
     * @param busqueda texto libre sobre título/isbn, opcional
     * @param pageable página y tamaño solicitados
     * @return página del inventario con su total
     */
    @Transactional(readOnly = true)
    public Page<ReportInventoryResponseDTO> reportInventoryPaginated(
            Integer categoryId, String statusStock, String busqueda, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportInventoryProjection> projections =
                loanProcRepo.fnReportInventoryPaginated(categoryId, statusStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null, limit, offset);
        long total = loanProcRepo.countReportInventory(categoryId, statusStock, busqueda,
                        null, null, null, null, null, null, null, null, null, null, null);
        List<ReportInventoryResponseDTO> content = projections.stream()
                .map(p -> new ReportInventoryResponseDTO(
                        p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(),
                        p.getStockTotal(), p.getStockAvailable(), p.getStatusAvailability(),
                        p.getPublisherName(), p.getSupplierName(), p.getLanguageName(), p.getStatusBookName(),
                        p.getYearPublication(), p.getLocationPhysical()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // Sobrecarga con 8 filtros gerenciales.
    /**
     * Sobrecarga del inventario con los 8 filtros gerenciales completos
     * (editorial, proveedor, estado del libro, idioma, rango de años, rangos
     * de stock y ubicación), para el reporte de adquisiciones y expurgo.
     *
     * @param categoryId filtro opcional por categoría
     * @param statusStock filtro opcional por estado de stock
     * @param busqueda texto libre sobre título/isbn, opcional
     * @param publisherId filtro opcional por editorial
     * @param supplierId filtro opcional por proveedor
     * @param statusBookId filtro opcional por estado del libro
     * @param languageId filtro opcional por idioma
     * @param yearFrom año de publicación mínimo, opcional
     * @param yearUntil año de publicación máximo, opcional
     * @param stockTotalMin stock total mínimo, opcional
     * @param stockTotalMax stock total máximo, opcional
     * @param stockDispMin stock disponible mínimo, opcional
     * @param stockDispMax stock disponible máximo, opcional
     * @param location ubicación física parcial, opcional
     * @return inventario filtrado con stock total y disponible
     */
    @Transactional(readOnly = true)
    public List<ReportInventoryResponseDTO> reportInventory(
            Integer categoryId, String statusStock, String busqueda,
            Integer publisherId, Integer supplierId, Integer statusBookId, Integer languageId,
            Short yearFrom, Short yearUntil, Short stockTotalMin, Short stockTotalMax,
            Short stockDispMin, Short stockDispMax, String location) {
        return loanProcRepo.fnReportInventory(categoryId, statusStock, busqueda,
                        publisherId, supplierId, statusBookId, languageId, yearFrom, yearUntil,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, location).stream()
                .map(p -> new ReportInventoryResponseDTO(
                        p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(),
                        p.getStockTotal(), p.getStockAvailable(), p.getStatusAvailability(),
                        p.getPublisherName(), p.getSupplierName(), p.getLanguageName(), p.getStatusBookName(),
                        p.getYearPublication(), p.getLocationPhysical()))
                .toList();
    }

    /**
     * Versión paginada del inventario con los 8 filtros gerenciales.
     *
     * @param categoryId filtro opcional por categoría
     * @param statusStock filtro opcional por estado de stock
     * @param busqueda texto libre sobre título/isbn, opcional
     * @param publisherId filtro opcional por editorial
     * @param supplierId filtro opcional por proveedor
     * @param statusBookId filtro opcional por estado del libro
     * @param languageId filtro opcional por idioma
     * @param yearFrom año de publicación mínimo, opcional
     * @param yearUntil año de publicación máximo, opcional
     * @param stockTotalMin stock total mínimo, opcional
     * @param stockTotalMax stock total máximo, opcional
     * @param stockDispMin stock disponible mínimo, opcional
     * @param stockDispMax stock disponible máximo, opcional
     * @param location ubicación física parcial, opcional
     * @param pageable página y tamaño solicitados
     * @return página del inventario con su total
     */
    @Transactional(readOnly = true)
    public Page<ReportInventoryResponseDTO> reportInventoryPaginated(
            Integer categoryId, String statusStock, String busqueda,
            Integer publisherId, Integer supplierId, Integer statusBookId, Integer languageId,
            Short yearFrom, Short yearUntil, Short stockTotalMin, Short stockTotalMax,
            Short stockDispMin, Short stockDispMax, String location, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportInventoryProjection> projections =
                loanProcRepo.fnReportInventoryPaginated(categoryId, statusStock, busqueda,
                        publisherId, supplierId, statusBookId, languageId, yearFrom, yearUntil,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, location, limit, offset);
        long total = loanProcRepo.countReportInventory(categoryId, statusStock, busqueda,
                        publisherId, supplierId, statusBookId, languageId, yearFrom, yearUntil,
                        stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, location);
        List<ReportInventoryResponseDTO> content = projections.stream()
                .map(p -> new ReportInventoryResponseDTO(
                        p.getBookId(), p.getTitle(), p.getIsbn(), p.getAuthorName(), p.getCategoryName(),
                        p.getStockTotal(), p.getStockAvailable(), p.getStatusAvailability(),
                        p.getPublisherName(), p.getSupplierName(), p.getLanguageName(), p.getStatusBookName(),
                        p.getYearPublication(), p.getLocationPhysical()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * Préstamos vencidos con días de atraso y multa estimada, para la
     * gestión de cobranza en mostrador.
     *
     * @param daysAtrasoMin atraso mínimo en días para incluir, opcional
     * @param busqueda texto libre sobre lector o libro, opcional
     * @return vencidos con atraso y multa estimada
     */
    @Transactional(readOnly = true)
    public List<ReportOverduesResponseDTO> reportLoansOverdues(Integer daysAtrasoMin, String busqueda) {
        return loanProcRepo.fnReportLoansOverdues(daysAtrasoMin, busqueda).stream()
                .map(p -> new ReportOverduesResponseDTO(
                        p.getLoanId(), p.getUserName(), p.getUserEmail(), p.getBookTitle(), p.getBookIsbn(),
                        p.getDateLoanReturnEstimada() != null ? p.getDateLoanReturnEstimada().atOffset(ZoneOffset.UTC) : null,
                        p.getDaysAtraso(), p.getAmountFineEstimada()))
                .toList();
    }

    /**
     * Versión paginada de préstamos vencidos.
     *
     * @param daysAtrasoMin atraso mínimo en días para incluir, opcional
     * @param busqueda texto libre sobre lector o libro, opcional
     * @param pageable página y tamaño solicitados
     * @return página de vencidos con su total
     */
    @Transactional(readOnly = true)
    public Page<ReportOverduesResponseDTO> reportLoansOverduesPaginated(Integer daysAtrasoMin, String busqueda, Pageable pageable) {
        int limit = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportOverduesProjection> projections = loanProcRepo.fnReportLoansOverduesPaginated(daysAtrasoMin, busqueda, limit, offset);
        long total = loanProcRepo.countReportLoansOverdues(daysAtrasoMin, busqueda);
        List<ReportOverduesResponseDTO> content = projections.stream()
                .map(p -> new ReportOverduesResponseDTO(p.getLoanId(), p.getUserName(), p.getUserEmail(), p.getBookTitle(), p.getBookIsbn(),
                        p.getDateLoanReturnEstimada() != null ? p.getDateLoanReturnEstimada().atOffset(ZoneOffset.UTC) : null, p.getDaysAtraso(), p.getAmountFineEstimada()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * Categorías más demandadas con total y porcentaje, para decidir
     * adquisiciones. Límite nulo equivale a 10.
     *
     * @param limit tope de filas, 10 si es nulo
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @return categorías ordenadas por demanda
     */
    @Transactional(readOnly = true)
    public List<ReportCategoriesDemandedResponseDTO> reportCategoriesDemanded(
            Integer limit, OffsetDateTime from, OffsetDateTime until) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        return loanProcRepo.fnReportCategoriesDemanded(limitEffective, from, until).stream()
                .map(p -> new ReportCategoriesDemandedResponseDTO(p.getCategoryId(), p.getCategoryName(), p.getTotalLoans(), p.getPercentage()))
                .toList();
    }

    /**
     * Versión paginada de categorías demandadas.
     *
     * @param limit tope del ranking base, 10 si es nulo
     * @param from inicio del rango, puede ser nulo
     * @param until fin del rango, puede ser nulo
     * @param pageable página y tamaño solicitados
     * @return página del ranking con su total
     */
    @Transactional(readOnly = true)
    public Page<ReportCategoriesDemandedResponseDTO> reportCategoriesDemandedPaginated(
            Integer limit, OffsetDateTime from, OffsetDateTime until, Pageable pageable) {
        Integer limitEffective = (limit != null) ? limit : LIMITE_REPORTE_DEFAULT;
        int pageSize = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        List<ReportCategoriesDemandedProjection> projections = loanProcRepo.fnReportCategoriesDemandedPaginated(limitEffective, from, until, pageSize, offset);
        long total = loanProcRepo.countReportCategoriesDemanded(limitEffective, from, until);
        List<ReportCategoriesDemandedResponseDTO> content = projections.stream()
                .map(p -> new ReportCategoriesDemandedResponseDTO(p.getCategoryId(), p.getCategoryName(), p.getTotalLoans(), p.getPercentage()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    private ReportDelinquencyResponseDTO toDTO(ReportDelinquencyProjection p) {
        return new ReportDelinquencyResponseDTO(
                p.getUserId(),
                p.getName(),
                p.getLastName(),
                p.getEmail(),
                p.getAmountTotalAdeudado(),
                p.getQuantityFinesPendientes(),
                p.getDaysAtrasoPromedio());
    }

    private ReportUsageByPeriodResponseDTO toDTO(ReportUsageByPeriodProjection p) {
        return new ReportUsageByPeriodResponseDTO(
                p.getPeriod() != null ? p.getPeriod().atOffset(ZoneOffset.UTC) : null,
                p.getTotalLoans(),
                p.getTotalLoanReturns());
    }

    private void validateLimitLoans(Long userId) {
        int maxLoans = configurationSystemService.getValueEntero("max_prestamos_usuario");
        List<LoanActiveProjection> actives = loanProcRepo.fnListLoansActivesByUser(userId);
        if (actives.size() >= maxLoans) {
            throw new LimitLoansExceededException(
                    "El usuario ya tiene " + actives.size() + " préstamos activos. El máximo permitido es " + maxLoans + ".");
        }
    }
}