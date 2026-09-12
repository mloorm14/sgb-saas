package com.uteq.backend.service;

import com.uteq.backend.dto.DamageDetailResponseDTO;
import com.uteq.backend.dto.LoanReturnFullResponseDTO;
import com.uteq.backend.dto.LoanReturnHistoryDTO;
import com.uteq.backend.dto.LoanReturnRequestDTO;
import com.uteq.backend.dto.EvidenceDamageFileDTO;
import com.uteq.backend.dto.EvidenceDamageResponseDTO;
import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.entity.StatusFine;
import com.uteq.backend.entity.EvidenceDamage;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Fine;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.RegistrationDamage;
import com.uteq.backend.entity.RegistrationDamageDetail;
import com.uteq.backend.entity.TypeDamage;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.EvidenceDamageRepository;
import com.uteq.backend.repository.StatusFineRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.FineRepository;
import com.uteq.backend.repository.LoanProcedureRepository;
import com.uteq.backend.repository.LoanRepository;
import com.uteq.backend.repository.RegistrationDamageDetailRepository;
import com.uteq.backend.repository.RegistrationDamageRepository;
import com.uteq.backend.repository.TypeDamageRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoanReturnService {

    private static final Logger log = LoggerFactory.getLogger(LoanReturnService.class);

    private static final String PRESTAMO_NO_ENCONTRADO = "Prestamo no encontrado: ";
    private static final String ESTADO_MULTA_PENDIENTE = "PENDIENTE";
    private static final String NOMBRE_DESCONOCIDO = "Desconocido";
    private static final int LIMITE_HISTORIAL = 10;
    private static final String CLAVE_MAX_TAMANO_EVIDENCIA_MB = "max_tamano_evidencia_mb";
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif");

    private final LoanRepository loanRepo;
    private final LoanProcedureRepository loanProcRepo;
    private final UserRepository userRepo;
    private final BookRepository bookRepo;
    private final StatusFineRepository statusFineRepo;
    private final FineRepository fineRepo;
    private final TypeDamageRepository typeDamageRepo;
    private final RegistrationDamageRepository registrationDamageRepo;
    private final RegistrationDamageDetailRepository registrationDamageDetailRepo;
    private final EvidenceDamageRepository evidenceDamageRepo;
    private final ConfigurationSystemService configurationSystemService;

    // La auditoria de registro_danos ya no se hace aqui: trg_auditoria_registro_danos
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    // El resumen humano de la devolucion completa (estado + multa atraso + multa
    // dano + cantidad de danos en una sola linea) desaparece como vista consolidada:
    // ver detalle en OBS-28.
    public LoanReturnService(LoanRepository loanRepo,
                             LoanProcedureRepository loanProcRepo,
                             UserRepository userRepo,
                             BookRepository bookRepo,
                             StatusFineRepository statusFineRepo,
                             FineRepository fineRepo,
                             TypeDamageRepository typeDamageRepo,
                             RegistrationDamageRepository registrationDamageRepo,
                             RegistrationDamageDetailRepository registrationDamageDetailRepo,
                             EvidenceDamageRepository evidenceDamageRepo,
                             ConfigurationSystemService configurationSystemService) {
        this.loanRepo = loanRepo;
        this.loanProcRepo = loanProcRepo;
        this.userRepo = userRepo;
        this.bookRepo = bookRepo;
        this.statusFineRepo = statusFineRepo;
        this.fineRepo = fineRepo;
        this.typeDamageRepo = typeDamageRepo;
        this.registrationDamageRepo = registrationDamageRepo;
        this.registrationDamageDetailRepo = registrationDamageDetailRepo;
        this.evidenceDamageRepo = evidenceDamageRepo;
        this.configurationSystemService = configurationSystemService;
    }

    @Transactional
    /**
     * Registra register loan return validando los datos de entrada antes de persistir cambios.
     *
     * @param loanId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param librarianId valor de entrada librarianId usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public LoanReturnFullResponseDTO registerLoanReturn(
            Long loanId, LoanReturnRequestDTO dto, Long librarianId) {

        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new EntityNotFoundException(PRESTAMO_NO_ENCONTRADO + loanId));

        if (loan.getDateLoanReturnReal() != null) {
            throw new IllegalStateException("El prestamo " + loanId + " ya fue devuelto.");
        }

        Map<String, Object> resultSp = loanProcRepo.spRegisterLoanReturn(loanId);
        Boolean huboFineAtraso = (Boolean) resultSp.get("o_hubo_multa");
        BigDecimal amountFineAtraso = resultSp.get("o_monto_multa") != null
                ? new BigDecimal(resultSp.get("o_monto_multa").toString())
                : null;

        boolean hayDamages = dto.statusLoanReturn() != null
                && "CON_DANO".equals(dto.statusLoanReturn())
                && dto.damages() != null
                && !dto.damages().isEmpty();

        boolean esPerdido = "PERDIDO".equals(dto.statusLoanReturn());

        BigDecimal amountFineDamage = BigDecimal.ZERO;
        List<DamageDetailResponseDTO> damagesRegistrados = new ArrayList<>();
        Long registrationDamageId = null;

        // precio_base obligatorio para cálculo porcentaje
        BigDecimal priceBook = BigDecimal.ZERO;
          try {
              Book lib = bookRepo.findById(loan.getBookId()).orElse(null);
              if (lib != null && lib.getPriceBase() != null) priceBook = lib.getPriceBase();
          } catch (Exception e) {
              // best-effort: sin precio base la multa por daño se calcula en cero
              log.debug("No se pudo resolver precio base del libro {}", loan.getBookId(), e);
          }

        if (hayDamages || esPerdido) {
            RegistrationDamage registration = new RegistrationDamage();
            registration.setLoanId(loanId);
            registration.setStatusLoanReturn(dto.statusLoanReturn());
            registration.setDescription(dto.description());
            registration.setLibrarianId(librarianId);
            registration.setDateRegistration(OffsetDateTime.now());
            registration = registrationDamageRepo.save(registration);
            registrationDamageId = registration.getId();

            if (hayDamages) {
                for (LoanReturnRequestDTO.DamageItemDTO item : dto.damages()) {
                    BigDecimal cobrado;
                    String nameDamage;
                    if (item.typeDamageId() != null) {
                        TypeDamage t = typeDamageRepo.findById(item.typeDamageId()).orElse(null);
                        if (t != null) {
                            nameDamage = t.getName();
                            if ("PORCENTAJE".equals(t.getTypeCost())) {
                                cobrado = priceBook.multiply(t.getValue()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                            } else {
                                cobrado = t.getValue();
                            }
                        } else {
                            // fallback para tests que usan precioCobrado directo
                            cobrado = item.priceCobrado() != null ? item.priceCobrado() : BigDecimal.ZERO;
                            nameDamage = NOMBRE_DESCONOCIDO;
                        }
                    } else {
                        // daño custom (nombreCustom) - valor viene del front como fijo
                        cobrado = item.priceCobrado() != null ? item.priceCobrado() : BigDecimal.ZERO;
                        nameDamage = item.nameCustom();
                    }
                    RegistrationDamageDetail detail = new RegistrationDamageDetail();
                    detail.setRegistrationDamageId(registration.getId());
                    detail.setTypeDamageId(item.typeDamageId());
                    detail.setNameCustom(item.nameCustom());
                    detail.setPriceCobrado(cobrado);
                    registrationDamageDetailRepo.save(detail);

                    amountFineDamage = amountFineDamage.add(cobrado);

                    damagesRegistrados.add(new DamageDetailResponseDTO(
                            detail.getId(),
                            nameDamage,
                            item.nameCustom(),
                            cobrado));
                }
            }

            if (esPerdido) {
                // Pérdida total = 100% del precio_base
                BigDecimal valueBook = priceBook.compareTo(BigDecimal.ZERO) > 0 ? priceBook : BigDecimal.valueOf(15.00);
                amountFineDamage = valueBook;

                damagesRegistrados.add(new DamageDetailResponseDTO(
                        null, "Libro perdido", "Libro perdido", valueBook));
            }

            if (amountFineDamage.compareTo(BigDecimal.ZERO) > 0) {
                Integer statusPendingId = statusFineRepo.findByName(ESTADO_MULTA_PENDIENTE)
                        .map(StatusFine::getId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Catalogo estados_multa sin fila '" + ESTADO_MULTA_PENDIENTE + "'"));

                Fine fineDamage = new Fine();
                fineDamage.setLoanId(loanId);
                fineDamage.setAmount(amountFineDamage);
                fineDamage.setStatusFineId(statusPendingId);
                fineDamage.setDateGenerated(OffsetDateTime.now());
                fineDamage.setObservations("Dano registrado: " + dto.statusLoanReturn());
                fineRepo.save(fineDamage);

                // (lookup de usuario removido: su resultado se descartaba sin uso)
            }
        }

        BigDecimal amountTotal = BigDecimal.ZERO;
        if (amountFineAtraso != null) amountTotal = amountTotal.add(amountFineAtraso);
        amountTotal = amountTotal.add(amountFineDamage);

        return new LoanReturnFullResponseDTO(
                loanId,
                registrationDamageId,
                huboFineAtraso != null && huboFineAtraso,
                amountFineAtraso,
                amountFineDamage.compareTo(BigDecimal.ZERO) > 0,
                amountFineDamage,
                amountTotal,
                damagesRegistrados);
    }

    @Transactional(readOnly = true)
    /**
         * Busca/lista recursos.
     * @return lista o pagina de resultados
     */
    public List<TypeDamageDTO> listTypesDamage() {
        return typeDamageRepo.findByActiveTrue().stream()
                .map(t -> new TypeDamageDTO(t.getId(), t.getName(),
                        t.getCategory()!=null ? t.getCategory().getId() : null,
                        t.getCategory()!=null ? t.getCategory().getName() : null,
                        t.getTypeCost(), t.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Procesa history loan returns y devuelve el resultado calculado por el backend.
     *
     * @param librarianId valor de entrada librarianId usado por la operacion para completar su regla de negocio
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<LoanReturnHistoryDTO> historyLoanReturns(Long librarianId) {
        List<RegistrationDamage> registrations = registrationDamageRepo
                .findTop10ByLibrarianIdOrderByDateRegistrationDesc(
                        librarianId, PageRequest.of(0, LIMITE_HISTORIAL));

        if (registrations.isEmpty()) return List.of();

        List<Long> loanIds = registrations.stream()
                .map(RegistrationDamage::getLoanId).distinct().toList();

        Map<Long, Loan> loansMap = loanRepo.findAllById(loanIds).stream()
                .collect(Collectors.toMap(Loan::getId, p -> p));

        Map<Long, Book> booksMap = bookRepo.findAllById(
                loansMap.values().stream().map(Loan::getBookId).distinct().toList())
                .stream().collect(Collectors.toMap(Book::getId, l -> l));

        Map<Long, User> usersMap = userRepo.findAllById(
                loansMap.values().stream().map(Loan::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        User librarian = userRepo.findById(librarianId).orElse(null);
        String nameBiblio = librarian != null
                ? (librarian.getName() + " " + librarian.getLastName()).trim()
                : NOMBRE_DESCONOCIDO;

        List<Long> registrationIds = registrations.stream().map(RegistrationDamage::getId).toList();
        Map<Long, BigDecimal> finesByRegistration = computeFinesByRegistration(registrationIds);

        return registrations.stream().map(rd -> {
            Loan p = loansMap.get(rd.getLoanId());
            if (p == null) return null;

            Book l = booksMap.get(p.getBookId());
            User u = usersMap.get(p.getUserId());

            return new LoanReturnHistoryDTO(
                    p.getId(),
                    l != null ? l.getTitle() : "Libro #" + p.getBookId(),
                    l != null ? l.getIsbn() : "",
                    u != null ? (u.getName() + " " + u.getLastName()).trim() : NOMBRE_DESCONOCIDO,
                    p.getDateLoan(),
                    p.getDateLoanReturnEstimada(),
                    p.getDateLoanReturnReal(),
                    rd.getStatusLoanReturn(),
                    finesByRegistration.getOrDefault(rd.getId(), BigDecimal.ZERO),
                    nameBiblio,
                    rd.getDateRegistration());
        }).filter(Objects::nonNull).toList();
    }

    private Map<Long, BigDecimal> computeFinesByRegistration(List<Long> registrationIds) {
        if (registrationIds.isEmpty()) return Map.of();

        // NOTA: retorna mapa vacío a propósito (las multas por daño se
        // registran sueltas, sin vínculo al registro). Si a futuro se
        // vinculan, poblar aquí agrupando por registro.
        return Map.of();
    }

    // ── Evidencia fotográfica ──────────────────────────────

    @Transactional
    /**
     * Procesa upload evidence y devuelve el resultado calculado por el backend.
     *
     * @param registrationDamageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param file archivo recibido en la peticion y usado como contenido principal de la operacion
     * @param librarianId valor de entrada librarianId usado por la operacion para completar su regla de negocio
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public EvidenceDamageResponseDTO uploadEvidence(Long registrationDamageId, MultipartFile file, Long librarianId) {
        RegistrationDamage registration = registrationDamageRepo.findById(registrationDamageId)
                .orElseThrow(() -> new EntityNotFoundException("Registro de daño no encontrado: " + registrationDamageId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de imagen");
        }

        String contentType = file.getContentType();
        if (contentType == null || !TIPOS_EVIDENCIA_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de imagen no permitido: " + contentType
                            + ". Solo se admiten JPG, JPEG, PNG, WebP y AVIF.");
        }

        int maxSizeMb = configurationSystemService.getValueEntero(CLAVE_MAX_TAMANO_EVIDENCIA_MB);
        long maxSizeBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "La imagen excede el tamaño máximo permitido de " + maxSizeMb + " MB");
        }

        EvidenceDamage evidence = new EvidenceDamage();
        evidence.setRegistrationDamageId(registrationDamageId);
        evidence.setFileName(file.getOriginalFilename());
        evidence.setFileType(contentType);
        try {
            evidence.setFileBytes(file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Error al leer el archivo: " + e.getMessage());
        }
        evidence.setSubido(OffsetDateTime.now());
        evidence = evidenceDamageRepo.save(evidence);

        return new EvidenceDamageResponseDTO(
                evidence.getId(),
                evidence.getRegistrationDamageId(),
                evidence.getFileName(),
                evidence.getFileType(),
                evidence.getSubido());
    }

    @Transactional(readOnly = true)
    /**
     * Consulta list evidences usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param registrationDamageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return lista de resultados que coincide con la consulta solicitada
     */
    public List<EvidenceDamageResponseDTO> listEvidences(Long registrationDamageId) {
        return evidenceDamageRepo.findByRegistrationDamageId(registrationDamageId).stream()
                .map(e -> new EvidenceDamageResponseDTO(
                        e.getId(), e.getRegistrationDamageId(),
                        e.getFileName(), e.getFileType(), e.getSubido()))
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Consulta get file evidence usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public EvidenceDamageResponseDTO getFileEvidence(Long id) {
        EvidenceDamage evidence = evidenceDamageRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evidencia no encontrada: " + id));
        return new EvidenceDamageResponseDTO(
                evidence.getId(), evidence.getRegistrationDamageId(),
                evidence.getFileName(), evidence.getFileType(), evidence.getSubido());
    }

    @Transactional(readOnly = true)
    /**
     * Consulta get file binario usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public EvidenceDamageFileDTO getFileBinario(Long id) {
        EvidenceDamage evidence = evidenceDamageRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evidencia no encontrada: " + id));
        return new EvidenceDamageFileDTO(evidence.getFileType(), evidence.getFileBytes());
    }
}
