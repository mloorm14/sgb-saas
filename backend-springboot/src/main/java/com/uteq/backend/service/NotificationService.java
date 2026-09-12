package com.uteq.backend.service;

import com.uteq.backend.dto.NotificationResponseDTO;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Notification;
import com.uteq.backend.entity.Loan;
import com.uteq.backend.entity.Reservation;
import com.uteq.backend.entity.TypeNotification;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.NotificationRepository;
import com.uteq.backend.repository.TypeNotificationRepository;
import com.uteq.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Genera las alertas VENCIMIENTO, MULTA y RESERVA_CADUCADA, y expone el
 * listado por usuario. Cada método público corresponde a un disparador distinto.
 */
@Service
public class NotificationService {

    private static final String TIPO_VENCIMIENTO = "VENCIMIENTO";
    private static final String TIPO_MULTA = "MULTA";
    private static final String TIPO_RESERVA_CADUCADA = "RESERVA_CADUCADA";
    private static final String TIPO_COMPROBANTE_PAGO = "COMPROBANTE_PAGO";
    private static final String TIPO_DISPONIBLE = "DISPONIBLE";
    private static final String ROL_LECTOR = "LECTOR";
    private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado: ";
    private static final String TIPO_NO_ENCONTRADO = "Catalogo tipos_notificacion sin fila '";
    private static final String CIERRE_DIV = "</div>";

    private final NotificationRepository notificationRepo;
    private final TypeNotificationRepository typeNotificationRepo;
    private final UserRepository userRepo;
    private final BookRepository bookRepo;
    private final EmailService emailService;

    // Correos automáticos desactivados por defecto; solo se reactivan por configuración.
    @Value("${notificaciones.email.habilitado:false}")
    private boolean emailEnabled;

    public NotificationService(NotificationRepository notificationRepo,
                                TypeNotificationRepository typeNotificationRepo,
                                UserRepository userRepo,
                                BookRepository bookRepo,
                                EmailService emailService) {
        this.notificationRepo = notificationRepo;
        this.typeNotificationRepo = typeNotificationRepo;
        this.userRepo = userRepo;
        this.bookRepo = bookRepo;
        this.emailService = emailService;
    }

    /**
     * Genera la alerta de préstamo por vencer (job cada 60s). Es idempotente:
     * no hace nada si ya existe una notificación VENCIMIENTO para el
     * préstamo, para no spamear al lector en cada corrida del scheduler.
     *
     * @param loan préstamo próximo a vencer, con usuario, libro y fecha estimada
     */
    @Transactional
    /**
     * Generates notification.
     *
     * @param loan loan used to scope this notification
     */
    public void generateAlertaDue(Loan loan) {
        Integer typeId = idType(TIPO_VENCIMIENTO);
        if (notificationRepo.existsByLoanIdAndTypeNotificationId(loan.getId(), typeId)) {
            return;
        }

        String title = titleBook(loan.getBookId());
        String message = "Tu préstamo de \"" + title + "\" vence el " + loan.getDateLoanReturnEstimada()
                + ". Recuerda devolverlo o renovarlo a tiempo.";

        createYSend(loan.getUserId(), loan.getId(), typeId, message,
                "Tu préstamo está por vencer");
    }

    /**
     * Notifica la multa recién generada por una devolución con atraso.
     * Se dispara por evento desde {@code sp_registrar_devolucion}, sin
     * deduplicación: cada multa genera exactamente una alerta.
     *
     * @param userId dueño del préstamo multado, receptor de la alerta
     * @param loanId préstamo que originó la multa, para contexto del mensaje
     * @param amount monto de la multa generada, incluido en el mensaje
     */
    @Transactional
    /**
     * Notifies notification.
     *
     * @param userId numeric identifier used to scope this notification
     * @param loanId numeric identifier used to scope this notification
     * @param amount monetary amount used to scope this notification
     */
    public void notifyFine(Long userId, Long loanId, BigDecimal amount) {
        String message = "Se generó una multa de $" + amount + " asociada a tu préstamo #" + loanId
                + " por atraso en la devolución.";
        createYSend(userId, loanId, idType(TIPO_MULTA), message, "Se generó una multa en tu cuenta");
    }

    /**
     * Notifica cada reservación vencida encontrada en la corrida actual del
     * scheduler (cada 15 min). La notificación in-app siempre se persiste;
     * el correo depende de la configuración (deshabilitado por defecto).
     *
     * @param reservation reservación caducada con usuario y libro para el mensaje
     */
    @Transactional
    /**
     * Notifies notification.
     *
     * @param reservation reservation used to scope this notification
     */
    public void notifyReservationExpired(Reservation reservation) {
        String title = titleBook(reservation.getBookId());
        String message = "Tu reserva de \"" + title + "\" caducó porque no se retiró dentro del plazo.";
        createYSend(reservation.getUserId(), null, idType(TIPO_RESERVA_CADUCADA), message,
                "Tu reserva caducó");
    }

    // ── GET /notificaciones/usuario/{id} ──
    // Propio vs cualquiera: LECTOR solo sus propias notificaciones.
    /**
     * Lista paginada de notificaciones de un usuario, con el control
     * "propio vs cualquiera": un LECTOR solo ve las suyas.
     *
     * @param userId dueño de las notificaciones a listar
     * @param authentication autenticación vigente, usada para resolver el rol y el usuario propio
     * @param pageable paginación y orden solicitados
     * @return página de notificaciones del usuario
     * @throws AuthorizationDeniedException si un LECTOR pide notificaciones ajenas
     */
    @Transactional(readOnly = true)
    /**
     * Lists notification Response DTO records.
     *
     * @param userId numeric identifier used to scope this notification Response DTO records
     * @param authentication authentication of the caller used to scope this notification Response DTO records
     * @param pageable pagination information used to scope this notification Response DTO records
     * @return page of notification Response data transfer object for the requested pagination
     */
    public Page<NotificationResponseDTO> listByUser(Long userId, Authentication authentication, Pageable pageable) {
        validateAccessUser(userId, authentication);
        return notificationRepo.findByUserId(userId, pageable).map(this::toDTO);
    }

    /**
     * Envía el comprobante de pago de una multa por correo al usuario dueño,
     * con el HTML del recibo (multa, monto, fecha y estado REGISTRADO).
     *
     * @param userId receptor del comprobante, debe existir
     * @param fineId multa pagada, mostrada en el recibo
     * @param amountPaid monto cobrado, mostrado en el recibo
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional
    /**
     * Notifies notification.
     *
     * @param userId numeric identifier used to scope this notification
     * @param fineId numeric identifier used to scope this notification
     * @param amountPaid monetary amount used to scope this notification
     * @throws EntityNotFoundException when the notification cannot be processed with the given input
     */
    public void notifyReceiptPayment(Long userId, Long fineId, BigDecimal amountPaid) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));

        String asunto = "Comprobante de pago de multa - SGB";
        String dateTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy, HH:mm:ss"));

        String html = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;\">"
                + "<div style=\"background:#1a237e;color:white;padding:24px;text-align:center;\">"
                + "<h1 style=\"margin:0;font-size:22px;\">Sistema de Gestion Bibliotecaria</h1>"
                + "<p style=\"margin:8px 0 0;font-size:14px;opacity:0.9;\">Comprobante de Pago de Multa</p>"
                + CIERRE_DIV
                + "<div style=\"padding:24px;\">"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 16px;\">Fecha: <strong>" + dateTime + "</strong></p>"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 4px;\">Cliente: <strong>"
                + user.getName() + " " + user.getLastName() + "</strong></p>"
                + "<p style=\"color:#555;font-size:13px;margin:0 0 20px;\">" + user.getEmail() + "</p>"
                + "<hr style=\"border:none;border-top:1px solid #e0e0e0;margin:0 0 20px;\">"
                + "<table style=\"width:100%;font-size:14px;\">"
                + "<tr><td style=\"padding:6px 0;color:#555;\">Multa #</td><td style=\"padding:6px 0;text-align:right;font-weight:bold;\">" + fineId + "</td></tr>"
                + "<tr><td style=\"padding:6px 0;color:#555;\">Monto pagado</td><td style=\"padding:6px 0;text-align:right;font-weight:bold;color:#1565c0;\">$" + amountPaid + "</td></tr>"
                + "</table>"
                + "<div style=\"background:#e8f5e9;border-radius:8px;padding:16px;text-align:center;margin-top:20px;\">"
                + "<p style=\"margin:0;color:#2e7d32;font-size:13px;\">Estado del pago</p>"
                + "<p style=\"margin:4px 0 0;color:#1b5e20;font-size:18px;font-weight:bold;\">REGISTRADO</p>"
                + CIERRE_DIV
                + CIERRE_DIV
                + "<div style=\"background:#f5f5f5;padding:16px;text-align:center;font-size:11px;color:#999;\">"
                + "Sistema de Gestion Bibliotecaria &copy; " + java.time.Year.now().getValue()
                + CIERRE_DIV
                + CIERRE_DIV;

        createYSend(userId, null, idType(TIPO_COMPROBANTE_PAGO), html, asunto);
    }

    /**
     * Avisa a un suscriptor que el libro que esperaba ya está disponible.
     * A diferencia de las alertas automáticas, esta vía es manual y sí
     * intenta el correo cuando el envío está permitido.
     *
     * @param userId suscriptor a avisar, debe existir
     * @param bookId libro disponible (informativo, puede ir nulo en el registro)
     * @param title título del libro, incluido en el mensaje
     * @throws EntityNotFoundException si el usuario no existe
     */
    @Transactional
    /**
     * Notifies notification.
     *
     * @param userId numeric identifier used to scope this notification
     * @param bookId numeric identifier used to scope this notification
     * @param title text value used to scope this notification
     */
    public void notifyBookAvailable(Long userId, Long bookId, String title) {
        String message = "El libro \"" + title + "\" esta disponible ahora — reservalo antes que otros.";
        Integer typeId = idType(TIPO_DISPONIBLE);
        // Notificación manual: si permite envío, se intenta correo.
        createYSendAvailable(userId, null, typeId, message, "Libro disponible");
    }

    private void createYSendAvailable(Long userId, Long loanId, Integer typeNotificationId, String message, String asunto) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));
        String bodyHtml = message.startsWith("<") ? message : "<p>" + message + "</p>";
        boolean enviado = false;
        String error = null;
        try {
            enviado = emailService.sendEmail(user.getEmail(), asunto, bodyHtml);
        } catch (Exception e) {
            error = e.getMessage();
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setLoanId(loanId);
        notification.setTypeNotificationId(typeNotificationId);
        notification.setMessage(message);
        notification.setEnviadoOk(enviado);
        if (enviado) {
            notification.setErrorEnvio(null);
        } else if (error != null) {
            notification.setErrorEnvio(error);
        } else {
            notification.setErrorEnvio("Fallo envio correo disponible");
        }
        notification.setDateEnvio(enviado ? OffsetDateTime.now() : null);
        notification.setCreated(OffsetDateTime.now());
        notificationRepo.save(notification);
    }

    // Correos automáticos desactivados: solo se crea la notificación in-app.
    // Para DISPONIBLE (manual) se usa crearYEnviarDisponible con email activo.
    private void createYSend(Long userId, Long loanId, Integer typeNotificationId, String message, String asunto) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + userId));

        String bodyHtml = message.startsWith("<") ? message : "<p>" + message + "</p>";
        // Correo automático desactivado por defecto (ver emailHabilitado).
        boolean enviado = false;
        if (emailEnabled) {
            enviado = emailService.sendEmail(user.getEmail(), asunto, bodyHtml);
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setLoanId(loanId);
        notification.setTypeNotificationId(typeNotificationId);
        notification.setMessage(message);
        notification.setEnviadoOk(enviado);
        if (enviado) {
            notification.setErrorEnvio(null);
        } else if (emailEnabled) {
            notification.setErrorEnvio("Error al enviar correo");
        } else {
            notification.setErrorEnvio("Correo automático desactivado (solo verificación activa) - ver NotificacionService.crearYEnviar");
        }
        notification.setDateEnvio(enviado ? OffsetDateTime.now() : null);
        notification.setCreated(OffsetDateTime.now());
        notificationRepo.save(notification);
    }

    private String titleBook(Long bookId) {
        return bookRepo.findById(bookId).map(Book::getTitle).orElse("(libro no encontrado)");
    }

    private Integer idType(String name) {
        return typeNotificationRepo.findByName(name)
                .map(TypeNotification::getId)
                .orElseThrow(() -> new IllegalStateException(TIPO_NO_ENCONTRADO + name + "'"));
    }

    private void validateAccessUser(Long userIdSolicitado, Authentication authentication) {
        boolean esReader = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + ROL_LECTOR));
        if (!esReader) {
            return;
        }
        Long idOwn = userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException(USUARIO_NO_ENCONTRADO + authentication.getName()))
                .getId();
        if (!idOwn.equals(userIdSolicitado)) {
            throw new AuthorizationDeniedException("Un LECTOR solo puede consultar sus propias notificaciones.");
        }
    }

    private NotificationResponseDTO toDTO(Notification n) {
        return new NotificationResponseDTO(
                n.getId(), n.getLoanId(), n.getTypeNotificationId(),
                n.getMessage(), n.getDateEnvio(), n.isEnviadoOk(), n.getCreated());
    }
}
