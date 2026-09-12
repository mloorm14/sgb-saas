package com.uteq.backend.service;

import com.uteq.backend.dto.FineActionResponseDTO;
import com.uteq.backend.dto.FineDetailResponseDTO;
import com.uteq.backend.dto.FineResponseDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Fine;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineProcedureRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.repository.projection.SummaryFinancialFinesProjection;
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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

@Service
public class FineService {

    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String PREFIJO_ROL = "ROLE_";
    private static final Set<String> ROLES_ANULACION = Set.of("GERENTE", "ADMIN");

    private final FineRepository fineRepo;
    private final FineProcedureRepository fineProcRepo;
    private final UserRepository userRepo;
    private final BookRepository bookRepo;
    private final LoanRepository loanRepo;

    // La auditoria de esta tabla ya no se hace aqui: trg_auditoria_multas
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    // sp_anular_multa (db/procs/sp_anular_multa.sql) sigue con su propio INSERT
    // manual interno -- eso es SQL dentro del procedimiento, no Java, y ya
    // estaba documentado como duplicacion aceptada en db/auditoria-triggers.sql
    // seccion 3; no se toca aqui.
    public FineService(FineRepository fineRepo,
                        FineProcedureRepository fineProcRepo,
                        UserRepository userRepo,
                        BookRepository bookRepo,
                        LoanRepository loanRepo) {
        this.fineRepo = fineRepo;
        this.fineProcRepo = fineProcRepo;
        this.userRepo = userRepo;
        this.bookRepo = bookRepo;
        this.loanRepo = loanRepo;
    }

    /**
     * Lista las multas de un usuario en forma resumida y paginada.
     * Aplica control de acceso por rol antes de consultar: un LECTOR solo
     * puede ver sus propias multas, otros roles pueden ver las de cualquiera.
     *
     * @param userId identificador del dueño de las multas a listar
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados por el cliente
     * @return página de multas en formato resumido
     * @throws AuthorizationDeniedException si un LECTOR pide multas de otro usuario
     */
    @Transactional(readOnly = true)
    /**
     * Lists fine Response DTO records.
     *
     * @param userId numeric identifier used to scope this fine Response DTO records
     * @param authentication authentication of the caller used to scope this fine Response DTO records
     * @param pageable pagination information used to scope this fine Response DTO records
     * @return page of fine Response data transfer object for the requested pagination
     */
    public Page<FineResponseDTO> listByUser(Long userId, Authentication authentication, Pageable pageable) {
        validateAccessUser(userId, authentication);
        return fineRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    /**
     * Lista las multas de un usuario con detalle enriquecido (libro, fechas
     * del préstamo, días de atraso y saldo), para la vista de gestión.
     * Comparte el mismo control de acceso por rol que {@link #listByUser}.
     *
     * @param userId identificador del dueño de las multas a listar
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados por el cliente
     * @return página de multas con detalle de préstamo y libro
     * @throws AuthorizationDeniedException si un LECTOR pide multas de otro usuario
     */
    @Transactional(readOnly = true)
    /**
     * Lists fine detail Response DTO records.
     *
     * @param userId numeric identifier used to scope this fine detail Response DTO records
     * @param authentication authentication of the caller used to scope this fine detail Response DTO records
     * @param pageable pagination information used to scope this fine detail Response DTO records
     * @return page of fine detail Response data transfer object for the requested pagination
     */
    public Page<FineDetailResponseDTO> listDetailByUser(Long userId, Authentication authentication, Pageable pageable) {
        validateAccessUser(userId, authentication);
        return fineRepo.findByUserId(userId, pageable).map(this::toDetailDTO);
    }

    /**
     * Registra un pago parcial contra una multa sin saldarla por completo.
     * Delega en el procedimiento almacenado {@code sp_pago_parcial_multa},
     * que valida el monto y actualiza el saldo pendiente a nivel de motor.
     *
     * @param fineId identificador de la multa a abonar
     * @param amountPaid monto del abono parcial, debe ser positivo y no superar el saldo
     * @return mapa con las salidas del procedimiento (identificadores y estado resultante)
     */
    @Transactional
    /**
     * Handles payment Parcial.
     *
     * @param fineId numeric identifier used to scope this payment Parcial
     * @param amountPaid monetary amount used to scope this payment Parcial
     * @return Map<String, Object> reflecting the state after the operation
     */
    public Map<String, Object> paymentParcial(Long fineId, BigDecimal amountPaid) {
        return fineProcRepo.spPaymentParcialFine(fineId, amountPaid);
    }

    /**
     * Paga totalmente una multa y, si era la única pendiente del usuario,
     * lo desbloquea para nuevos préstamos. La lógica vive en
     * {@code sp_pagar_multa}; aquí solo se adapta su salida al DTO.
     *
     * @param fineId identificador de la multa a pagar
     * @return acción resultante con el id de la multa y si el usuario quedó desbloqueado
     */
    @Transactional
    /**
     * Pays fine Accion Response data transfer object.
     *
     * @param fineId numeric identifier used to scope this fine Accion Response data transfer object
     * @return fine Accion Response data transfer object reflecting the state after the operation
     */
    public FineActionResponseDTO pay(Long fineId) {
        Map<String, Object> result = fineProcRepo.spPayFine(fineId);
        return new FineActionResponseDTO(
                (Long) result.get("o_multa_id"),
                (Boolean) result.get("o_usuario_desbloqueado"));
    }

    /**
     * Anula una multa con motivo registrado. Solo GERENTE o ADMIN pueden
     * ejecutarla: el rol se resuelve desde la autenticación y se envía al
     * procedimiento {@code sp_anular_multa} para auditoría.
     *
     * @param fineId identificador de la multa a anular
     * @param reason justificación de la anulación, queda registrada en la multa
     * @param authentication autenticación vigente, de donde se extrae el rol ejecutor
     * @return acción resultante con el id de la multa y si el usuario quedó desbloqueado
     * @throws AuthorizationDeniedException si el ejecutor no es GERENTE ni ADMIN
     */
    @Transactional
    /**
     * Voids fine Accion Response data transfer object.
     *
     * @param fineId numeric identifier used to scope this fine Accion Response data transfer object
     * @param reason text value used to scope this fine Accion Response data transfer object
     * @param authentication authentication of the caller used to scope this fine Accion Response data transfer object
     * @return fine Accion Response data transfer object reflecting the state after the operation
     */
    public FineActionResponseDTO annul(Long fineId, String reason, Authentication authentication) {
        String roleExecutor = resolveRoleCancellation(authentication);
        Map<String, Object> result = fineProcRepo.spVoidFine(fineId, reason, roleExecutor);
        return new FineActionResponseDTO(
                (Long) result.get("o_multa_id"),
                (Boolean) result.get("o_usuario_desbloqueado"));
    }

    /**
     * Resuelve el dueño de una multa navegando multa → préstamo → usuario.
     * Se usa para validar acceso y para notificar al lector correcto.
     *
     * @param fineId identificador de la multa cuyo dueño se busca
     * @return identificador del usuario dueño del préstamo multado
     * @throws EntityNotFoundException si la multa o su préstamo no existen
     */
    @Transactional(readOnly = true)
    /**
     * Resolves fine.
     *
     * @param fineId numeric identifier used to scope this fine
     * @return identifier of the affected record
     * @throws EntityNotFoundException when the fine cannot be processed with the given input
     */
    public Long resolveUserIdFine(Long fineId) {
        Fine fine = fineRepo.findById(fineId)
                .orElseThrow(() -> new EntityNotFoundException("Multa no encontrada: " + fineId));
        Loan loan = loanRepo.findById(fine.getLoanId())
                .orElseThrow(() -> new EntityNotFoundException("Prestamo no encontrado: " + fine.getLoanId()));
        return loan.getUserId();
    }

    /**
     * Arma el resumen financiero de multas para el dashboard gerente:
     * total recaudado y pendiente en el rango pedido, total generado hoy
     * y los 5 pagos más recientes. Combina tres funciones de base de datos.
     *
     * @param from inicio del rango del resumen, inclusivo
     * @param until fin del rango del resumen, inclusivo
     * @return resumen con recaudado, pendiente, generado hoy y pagos recientes
     */
    @Transactional(readOnly = true)
    /**
     * Handles report summary Financiero.
     *
     * @param from date-time bound used to scope this report summary Financiero
     * @param until date-time bound used to scope this report summary Financiero
     * @return summary Financiero fines Response data transfer object reflecting the state after the operation
     */
    public SummaryFinancialFinesResponseDTO reportSummaryFinancial(OffsetDateTime from, OffsetDateTime until) {
        SummaryFinancialFinesProjection summary = fineProcRepo.fnReportSummaryFinancial(from, until);

        // Total generado hoy: SUM(monto) de multas generadas hoy
        java.time.OffsetDateTime startToday = java.time.OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.time.OffsetDateTime finToday = startToday.plusDays(1);
        SummaryFinancialFinesProjection today = fineProcRepo.fnReportSummaryFinancial(startToday, finToday);
        BigDecimal totalGeneratedToday = today.getTotalRecaudado().add(today.getTotalPending());

        // Pagos recientes: últimos 5
        var paymentsRecientes = fineProcRepo.fnPaymentsRecientes(5).stream()
                .map(p -> new com.uteq.backend.dto.PaymentRecienteDTO(
                        p.getFineId(),
                        p.getAmountPaid(),
                        p.getDatePaid().atOffset(java.time.ZoneOffset.UTC),
                        p.getUserEmail(),
                        p.getUserName(),
                        p.getBookTitle()))
                .toList();

        return new SummaryFinancialFinesResponseDTO(
                summary.getTotalRecaudado(),
                summary.getTotalPending(),
                totalGeneratedToday,
                paymentsRecientes);
    }

    private String resolveRoleCancellation(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role.startsWith(PREFIJO_ROL))
                .map(role -> role.substring(PREFIJO_ROL.length()))
                .filter(ROLES_ANULACION::contains)
                .findFirst()
                .orElseThrow(() -> new AuthorizationDeniedException(
                        "Solo GERENTE o ADMIN puede anular multas."));
    }

    private void validateAccessUser(Long userIdSolicitado, Authentication authentication) {
        boolean esReader = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals(PREFIJO_ROL + ROL_LECTOR));
        if (!esReader) {
            return;
        }
        Long idOwn = resolveIdByEmail(authentication.getName());
        if (!idOwn.equals(userIdSolicitado)) {
            throw new AuthorizationDeniedException(
                    "Un LECTOR solo puede consultar sus propias multas.");
        }
    }

    private Long resolveIdByEmail(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + email));
        return user.getId();
    }

    private FineResponseDTO toDTO(Fine m) {
        return new FineResponseDTO(
                m.getId(), m.getLoanId(), m.getAmount(),
                m.getStatusFineId(), m.getDateGenerated(),
                m.getDatePaid(), m.getObservations());
    }

    private FineDetailResponseDTO toDetailDTO(Fine m) {
        Loan loan = loanRepo.findById(m.getLoanId()).orElse(null);
        String bookTitle = "";
        String bookIsbn = "";
        OffsetDateTime dateLoanStart = null;
        OffsetDateTime dateLoanFin = null;

        if (loan != null) {
            dateLoanStart = loan.getDateLoan();
            dateLoanFin = loan.getDateLoanReturnEstimada();
            Book book = bookRepo.findById(loan.getBookId()).orElse(null);
            if (book != null) {
                bookTitle = book.getTitle();
                bookIsbn = book.getIsbn() != null ? book.getIsbn() : "";
            }
        }

        int daysAtraso = 0;
        if (m.getDatePaid() != null && m.getDateGenerated() != null) {
            daysAtraso = (int) ChronoUnit.DAYS.between(m.getDateGenerated(), m.getDatePaid());
        } else if (m.getStatusFineId() != null && m.getStatusFineId() == 1) {
            daysAtraso = (int) ChronoUnit.DAYS.between(m.getDateGenerated(), OffsetDateTime.now());
        }

        BigDecimal amountPaid = m.getAmountPaid() != null ? m.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = m.getAmount().subtract(amountPaid);

        Map<Integer, String> statuses = Map.of(1, "PENDIENTE", 2, "PAGADA", 3, "ANULADA");
        String statusName = statuses.getOrDefault(m.getStatusFineId(), "DESCONOCIDO");

        return new FineDetailResponseDTO(
                m.getId(),
                m.getLoanId(),
                bookTitle,
                bookIsbn,
                m.getObservations(),
                m.getAmount(),
                amountPaid,
                balance,
                m.getStatusFineId(),
                statusName,
                m.getDateGenerated(),
                m.getDatePaid(),
                dateLoanStart,
                dateLoanFin,
                daysAtraso);
    }
}
