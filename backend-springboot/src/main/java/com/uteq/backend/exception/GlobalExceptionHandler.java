package com.uteq.backend.exception;

import com.uteq.backend.service.ChatbotRateLimitExceededException;
import com.uteq.backend.service.CodeVerificationInvalidException;
import com.uteq.backend.service.EmailDomainNotAllowedException;
import com.uteq.backend.service.EmailYaRegistradoException;
import com.uteq.backend.service.StatusReservationInitialNotConfiguredException;
import com.uteq.backend.service.LimitLoansExceededException;
import com.uteq.backend.service.LimitRenewalsExceededException;
import com.uteq.backend.service.LoginRateLimitExceededException;
import com.uteq.backend.service.MaterialReservadoException;
import com.uteq.backend.service.LoanOverdueException;
import com.uteq.backend.service.RefreshTokenInvalidException;
import com.uteq.backend.service.ServiceTemporalmenteNotAvailableException;
import com.uteq.backend.service.SessionChatNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mapea toda excepción de negocio a {@link ProblemDetail} (RFC 7807).
 * Spring fija el status HTTP desde {@code ProblemDetail.getStatus()}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Registro y préstamos ──────────────────────────────────

    @ExceptionHandler(EmailYaRegistradoException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex email address Ya Registrado Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleEmailYaRegistrado(EmailYaRegistradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailDomainNotAllowedException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex email address Dominio No Permitido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleEmailDomainNotAllowed(EmailDomainNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LimitLoansExceededException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Limite loans Excedido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleLimitLoansExceeded(LimitLoansExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(StatusReservationInitialNotConfiguredException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex status reservation Inicial No Configurado Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleStatusReservationInitialNotConfigured(StatusReservationInitialNotConfiguredException ex) {
        // Falta seed de configuración del sistema → 503.
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Autenticación ─────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Bad Credentials Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    // Intentos de login agotados en la ventana vigente → 429.
    @ExceptionHandler(LoginRateLimitExceededException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Login Rate Limit Excedido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleLoginRateLimitExceeded(LoginRateLimitExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Lector agotó el cupo de mensajes del chatbot en la ventana vigente → 429.
    @ExceptionHandler(ChatbotRateLimitExceededException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex chatbot Rate Limit Excedido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleChatbotRateLimitExceeded(ChatbotRateLimitExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Sesión de chat inexistente o de otro usuario → 404.
    @ExceptionHandler(SessionChatNotFoundException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex session Chat No Encontrada Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleSessionChatNotFound(SessionChatNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Refresh token inválido, expirado o de usuario inexistente → 401.
    @ExceptionHandler(RefreshTokenInvalidException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Refresh token Invalido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleRefreshTokenInvalid(RefreshTokenInvalidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // Cuenta bloqueada por multas pendientes → 423.
    @ExceptionHandler(LockedException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Locked Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleLocked(LockedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED,
                "Cuenta bloqueada por multas pendientes. Regularice su situación para continuar.");
    }

    // Cuenta inactiva o pendiente de verificación → 403.
    @ExceptionHandler(DisabledException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Disabled Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleDisabled(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Cuenta inactiva o pendiente de verificación.");
    }

    // Autenticado sin el rol requerido para el método → 403.
    @ExceptionHandler(AuthorizationDeniedException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Authorization Denied Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "No tiene permisos para realizar esta acción.");
    }

    // ── Validación ────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Constraint Violation Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Method Argument Not Valid Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            detalles.put(publicFieldName(fieldError.getField()), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        problem.setProperty("errores", detalles);
        return problem;
    }

    private String publicFieldName(String field) {
        return switch (field) {
            case "name" -> "nombre";
            case "lastName" -> "apellido";
            case "email" -> "correo";
            case "userId" -> "usuarioId";
            case "bookId" -> "libroId";
            case "daysLoan" -> "diasPrestamo";
            case "reservationId" -> "reservacionId";
            case "from" -> "desde";
            case "until" -> "hasta";
            case "tables" -> "tablas";
            case "format" -> "formato";
            default -> field;
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Http Message Not Readable Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleBodyMalformed(HttpMessageNotReadableException ex) {
        String detail = Objects.toString(
                ex.getMostSpecificCause().getMessage(), "El cuerpo de la solicitud no es válido");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    // ── Recursos ──────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Entity Not Found Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Ruta o recurso estático inexistente (ej. Swagger en perfil prod) → 404.
    @ExceptionHandler(NoResourceFoundException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex No Resource Found Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleNotResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    // ── Reglas de préstamo (cada motivo con su excepción) ─────

    @ExceptionHandler(LoanOverdueException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex loan Vencido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleLoanOverdue(LoanOverdueException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LimitRenewalsExceededException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Limite Renovaciones Excedido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleLimitRenewalsExceeded(LimitRenewalsExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MaterialReservadoException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Material Reservado Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleMaterialReservado(MaterialReservadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Código de verificación incorrecto o expirado → 400.
    @ExceptionHandler(CodeVerificationInvalidException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex code Verificacion Invalido Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleCodeVerificationInvalid(CodeVerificationInvalidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Dependencia externa caída (Redis/SMTP) → 503.
    @ExceptionHandler(ServiceTemporalmenteNotAvailableException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Servicio Temporalmente No Disponible Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleServiceTemporalmenteNotAvailable(ServiceTemporalmenteNotAvailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Acceso a datos ────────────────────────────────────────

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Invalid Data Access Api Usage Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleSortInvalid(InvalidDataAccessApiUsageException ex) {
        log.warn("Parámetro de ordenamiento inválido", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parámetro de ordenamiento inválido: " + ex.getMessage());
    }

    // Dependencia de datos agotada o caída → 503.
    @ExceptionHandler({DataAccessResourceFailureException.class, UncategorizedDataAccessException.class})
    /**
     * Handles Problem Detail.
     *
     * @param ex Data Access Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleDataAccessResourceFailure(DataAccessException ex) {
        log.error("Fallo de acceso a dependencia de datos (Redis/BD) {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        String root = Objects.toString(ex.getMostSpecificCause().getMessage(), ex.getMessage());
        String detail = "El servicio de almacenamiento temporal no está disponible. Intente más tarde."
                + (root != null ? " (" + root.substring(0, Math.min(200, root.length())) + ")" : "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Illegal Argument Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Illegal State Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.warn("Estado ilegal de negocio", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ── Stored procedures (SQLSTATE LBxxx) ────────────────────

    @ExceptionHandler({
            InvalidDataAccessResourceUsageException.class,
            UncategorizedSQLException.class,
            DataAccessException.class
    })
    /**
     * Handles Problem Detail.
     *
     * @param ex Data Access Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleStoredProcedureError(DataAccessException ex) {
        Objects.requireNonNull(ex, "el handler siempre recibe la excepción");
        Throwable causa = ex;
        while (causa != null && !(causa instanceof SQLException)) {
            causa = causa.getCause();
        }

        if (causa instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            HttpStatus status = switch (sqlState) {
                case "LB404" -> HttpStatus.NOT_FOUND;
                case "LB409" -> HttpStatus.CONFLICT;
                case "LB422" -> HttpStatus.UNPROCESSABLE_ENTITY;
                case "23503" -> HttpStatus.BAD_REQUEST;
                default -> null;
            };
            if (status != null) {
                return ProblemDetail.forStatusAndDetail(status, sqlEx.getMessage());
            }
        }

        String messageEx = "desconocida";
        if (ex != null) {
            messageEx = Objects.toString(ex.getMessage(), "sin mensaje");
        }
        log.error("Error no controlado en procedimiento almacenado: {}", messageEx, ex);
        String root = Objects.toString(ex.getMostSpecificCause().getMessage(), ex.getMessage());
        String detail = "Error interno del servidor: " + root.substring(0, Math.min(300, root.length()));
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    // Reenvía el status indicado por el servicio (400, 404, etc.).
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex org.springframework.web.server.Response Status Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.getStatusCode().value()),
                ex.getReason() != null ? ex.getReason() : ex.getMessage()
        );
    }

    // ── Fallback ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    /**
     * Handles Problem Detail.
     *
     * @param ex Exception used to scope this Problem Detail
     * @return Problem Detail reflecting the state after the operation
     */
    public ProblemDetail handleGenerica(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        String msg = ex.getMessage();
        String detail = "Error interno del servidor: " + (msg != null ? msg.substring(0, Math.min(300, msg.length())) : ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }
}
