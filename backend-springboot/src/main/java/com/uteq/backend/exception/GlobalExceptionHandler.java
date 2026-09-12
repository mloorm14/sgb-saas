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
         * Maneja el caso en que el correo ya est registrado en el sistema.
     * @param ex excepcin que indica que el correo ya est registrado
     * @return ProblemDetail con estado 409 y el mensaje del conflicto
     */
    public ProblemDetail handleEmailYaRegistrado(EmailYaRegistradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailDomainNotAllowedException.class)
    /**
         * Maneja el caso en que el correo pertenece a un dominio no permitido.
     * @param ex excepcin que indica que el dominio del correo no est permitido
     * @return ProblemDetail con estado 403 y el mensaje de denegacin
     */
    public ProblemDetail handleEmailDomainNotAllowed(EmailDomainNotAllowedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LimitLoansExceededException.class)
    /**
         * Maneja el caso en que el lector supera el tope mximo de prstamos activos.
     * @param ex excepcin que indica que se excedi el tope de prstamos
     * @return ProblemDetail con estado 409 y el mensaje del lmite
     */
    public ProblemDetail handleLimitLoansExceeded(LimitLoansExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(StatusReservationInitialNotConfiguredException.class)
    /**
         * Maneja la falta de configuracin inicial del estado de reservacin en el sistema.
     * @param ex excepcin que indica que la configuracin inicial del estado falta
     * @return ProblemDetail con estado 503 y el mensaje de la falta de configuracin
     */
    public ProblemDetail handleStatusReservationInitialNotConfigured(StatusReservationInitialNotConfiguredException ex) {
        // Falta seed de configuración del sistema → 503.
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Autenticación ─────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    /**
         * Maneja credenciales invlidas en el intento de autenticacin.
     * @param ex excepcin de credenciales incorrectas
     * @return ProblemDetail con estado 401 y mensaje de credenciales invlidas
     */
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    // Intentos de login agotados en la ventana vigente → 429.
    @ExceptionHandler(LoginRateLimitExceededException.class)
    /**
         * Maneja el agotamiento de intentos de login en la ventana vigente.
     * @param ex excepcin que indica que se agot el lmite de intentos
     * @return ProblemDetail con estado 429 y el mensaje del lmite
     */
    public ProblemDetail handleLoginRateLimitExceeded(LoginRateLimitExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Lector agotó el cupo de mensajes del chatbot en la ventana vigente → 429.
    @ExceptionHandler(ChatbotRateLimitExceededException.class)
    /**
         * Maneja el agotamiento del cupo de mensajes del chatbot en la ventana vigente.
     * @param ex excepcin que indica que se agot el rate limit del chatbot
     * @return ProblemDetail con estado 429 y el mensaje del lmite
     */
    public ProblemDetail handleChatbotRateLimitExceeded(ChatbotRateLimitExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Sesión de chat inexistente o de otro usuario → 404.
    @ExceptionHandler(SessionChatNotFoundException.class)
    /**
         * Maneja el caso en que la sesin de chat no existe o pertenece a otro usuario.
     * @param ex excepcin que indica que la sesin de chat no fue encontrada
     * @return ProblemDetail con estado 404 y el mensaje de sesin no encontrada
     */
    public ProblemDetail handleSessionChatNotFound(SessionChatNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Refresh token inválido, expirado o de usuario inexistente → 401.
    @ExceptionHandler(RefreshTokenInvalidException.class)
    /**
         * Maneja un token de refresco invlido, expirado o de usuario inexistente.
     * @param ex excepcin que indica que el refresh token es invlido
     * @return ProblemDetail con estado 401 y el mensaje del token invlido
     */
    public ProblemDetail handleRefreshTokenInvalid(RefreshTokenInvalidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // Cuenta bloqueada por multas pendientes → 423.
    @ExceptionHandler(LockedException.class)
    /**
         * Maneja una cuenta bloqueada por multas pendientes.
     * @param ex excepcin de cuenta bloqueada (Locked)
     * @return ProblemDetail con estado 423 y mensaje de cuenta bloqueada
     */
    public ProblemDetail handleLocked(LockedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED,
                "Cuenta bloqueada por multas pendientes. Regularice su situación para continuar.");
    }

    // Cuenta inactiva o pendiente de verificación → 403.
    @ExceptionHandler(DisabledException.class)
    /**
         * Maneja una cuenta inactiva o pendiente de verificacin.
     * @param ex excepcin de cuenta deshabilitada (Disabled)
     * @return ProblemDetail con estado 403 y mensaje de cuenta inactiva
     */
    public ProblemDetail handleDisabled(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Cuenta inactiva o pendiente de verificación.");
    }

    // Autenticado sin el rol requerido para el método → 403.
    @ExceptionHandler(AuthorizationDeniedException.class)
    /**
         * Maneja el caso en que el usuario autenticado carece del rol requerido para el mtodo.
     * @param ex excepcin de autorizacin denegada
     * @return ProblemDetail con estado 403 y mensaje de permisos insuficientes
     */
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "No tiene permisos para realizar esta acción.");
    }

    // ── Validación ────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    /**
         * Maneja violaciones de restricciones de validacin (anotaciones de Bean Validation).
     * @param ex excepcin con las restricciones violadas
     * @return ProblemDetail con estado 400 y los mensajes de violacin
     */
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
         * Maneja errores de validacin de argumentos del mtodo (MethodArgumentNotValid).
     * Recopila los errores de cada campo y los devuelve en la propiedad 'errores' del ProblemDetail.
     * @param ex excepcin con los errores de validacin por campo
     * @return ProblemDetail con estado 400, mensaje general y lista de errores por campo
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
         * Maneja un cuerpo de solicitud malformado o no legible.
     * @param ex excepcin de cuerpo de solicitud no legible
     * @return ProblemDetail con estado 400 y detalle del error del cuerpo
     */
    public ProblemDetail handleBodyMalformed(HttpMessageNotReadableException ex) {
        String detail = Objects.toString(
                ex.getMostSpecificCause().getMessage(), "El cuerpo de la solicitud no es válido");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    // ── Recursos ──────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    /**
         * Maneja el caso en que una entidad no fue encontrada en la base de datos.
     * @param ex excepcin de entidad no encontrada (EntityNotFoundException)
     * @return ProblemDetail con estado 404 y el mensaje de no encontrado
     */
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Ruta o recurso estático inexistente (ej. Swagger en perfil prod) → 404.
    @ExceptionHandler(NoResourceFoundException.class)
    /**
         * Maneja el caso en que un recurso esttico o ruta no existe (ej. Swagger en perfil prod).
     * @param ex excepcin de recurso no encontrado
     * @return ProblemDetail con estado 404 y mensaje de recurso no encontrado
     */
    public ProblemDetail handleNotResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    // ── Reglas de préstamo (cada motivo con su excepción) ─────

    @ExceptionHandler(LoanOverdueException.class)
    /**
         * Maneja un prstamo vencido que no ha sido devuelto.
     * @param ex excepcin que indica que el prstamo est vencido
     * @return ProblemDetail con estado 409 y el mensaje del prstamo vencido
     */
    public ProblemDetail handleLoanOverdue(LoanOverdueException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LimitRenewalsExceededException.class)
    /**
         * Maneja el caso en que se excede el tope de renovaciones del prstamo.
     * @param ex excepcin que indica que se excedi el lmite de renovaciones
     * @return ProblemDetail con estado 409 y el mensaje del lmite de renovaciones
     */
    public ProblemDetail handleLimitRenewalsExceeded(LimitRenewalsExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MaterialReservadoException.class)
    /**
         * Maneja el caso en que el material de un prstamo ya est reservado por otro usuario.
     * @param ex excepcin que indica que el material ya est reservado
     * @return ProblemDetail con estado 409 y el mensaje de material reservado
     */
    public ProblemDetail handleMaterialReservado(MaterialReservadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Código de verificación incorrecto o expirado → 400.
    @ExceptionHandler(CodeVerificationInvalidException.class)
    /**
         * Maneja un cdigo de verificacin incorrecto o expirado.
     * @param ex excepcin que indica que el cdigo de verificacin es invlido
     * @return ProblemDetail con estado 400 y el mensaje del cdigo invlido
     */
    public ProblemDetail handleCodeVerificationInvalid(CodeVerificationInvalidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Dependencia externa caída (Redis/SMTP) → 503.
    @ExceptionHandler(ServiceTemporalmenteNotAvailableException.class)
    /**
         * Maneja la indisponibilidad temporal de una dependencia externa (Redis/SMTP).
     * @param ex excepcin que indica que el servicio est temporalmente no disponible
     * @return ProblemDetail con estado 503 y el mensaje del servicio no disponible
     */
    public ProblemDetail handleServiceTemporalmenteNotAvailable(ServiceTemporalmenteNotAvailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Acceso a datos ────────────────────────────────────────

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    /**
         * Maneja un parmetro de ordenamiento invlido en una consulta.
     * @param ex excepcin con el parmetro de ordenamiento invlido
     * @return ProblemDetail con estado 400 y mensaje de parmetro de ordenamiento invlido
     */
    public ProblemDetail handleSortInvalid(InvalidDataAccessApiUsageException ex) {
        log.warn("Parámetro de ordenamiento inválido", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parámetro de ordenamiento inválido: " + ex.getMessage());
    }

    // Dependencia de datos agotada o caída → 503.
    @ExceptionHandler({DataAccessResourceFailureException.class, UncategorizedDataAccessException.class})
    /**
         * Maneja una falla de conexin o acceso a la dependencia de datos (Redis/BD).
     * Devuelve 503 con mensaje indicando indisponibilidad temporal del servicio de almacenamiento.
     * @param ex excepcin de fallo de acceso a datos
     * @return ProblemDetail con estado 503 y detalle de la falla de almacenamiento
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
         * Maneja un argumento ilegal pasado a un mtodo.
     * @param ex excepcin de argumento ilegal
     * @return ProblemDetail con estado 400 y el mensaje del argumento ilegal
     */
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    /**
         * Maneja un estado ilegal de negocio (operacin no vlida en el estado actual).
     * @param ex excepcin de estado ilegal
     * @return ProblemDetail con estado 409 y el mensaje del estado ilegal
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
         * Maneja errores de procedimientos almacenados en PostgreSQL (SQLSTATE LBxxx).
     * Traduce los cdigos SQLState a estados HTTP correspondientes (404, 409, 422, 400).
     * Si no reconoce el cdigo, devuelve 500 con el detalle del error.
     * @param ex excepcin de acceso a datos con error de procedimiento almacenado
     * @return ProblemDetail con el estado HTTP traducido del SQLState o 500 si es desconocido
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
     * Reenvía el status HTTP indicado por un ResponseStatusException del servicio.
     *
     * @param ex excepicon con el estado HTTP y razon del error
     * @return ProblemDetail con el cdigo de estado y razon indicados por la excepcin
    /**
     * Maneja handle response status y construye una respuesta consistente para el cliente.
     *
     * @param ex excepcion capturada que se transforma en una respuesta HTTP controlada
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
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
     * Fallback para cualquier excepcin no controlada.
     * Devuelve 500 con detalle del error (limitado a 300 caracteres).
     * @param ex excepicon no controlada
     * @return ProblemDetail con estado 500 y detalle del error interno
    /**
     * Maneja handle generica y construye una respuesta consistente para el cliente.
     *
     * @param ex excepcion capturada que se transforma en una respuesta HTTP controlada
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    public ProblemDetail handleGenerica(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        String msg = ex.getMessage();
        String detail = "Error interno del servidor: " + (msg != null ? msg.substring(0, Math.min(300, msg.length())) : ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }
}
