package com.uteq.backend.exception;

import com.uteq.backend.service.ChatbotRateLimitExcedidoException;
import com.uteq.backend.service.CodigoVerificacionInvalidoException;
import com.uteq.backend.service.CorreoDominioNoPermitidoException;
import com.uteq.backend.service.CorreoYaRegistradoException;
import com.uteq.backend.service.EstadoReservacionInicialNoConfiguradoException;
import com.uteq.backend.service.LimitePrestamosExcedidoException;
import com.uteq.backend.service.LimiteRenovacionesExcedidoException;
import com.uteq.backend.service.LoginRateLimitExcedidoException;
import com.uteq.backend.service.MaterialReservadoException;
import com.uteq.backend.service.PrestamoVencidoException;
import com.uteq.backend.service.RefreshTokenInvalidoException;
import com.uteq.backend.service.ServicioTemporalmenteNoDisponibleException;
import com.uteq.backend.service.SesionChatNoEncontradaException;
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

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ProblemDetail handleCorreoYaRegistrado(CorreoYaRegistradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CorreoDominioNoPermitidoException.class)
    public ProblemDetail handleCorreoDominioNoPermitido(CorreoDominioNoPermitidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LimitePrestamosExcedidoException.class)
    public ProblemDetail handleLimitePrestamosExcedido(LimitePrestamosExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EstadoReservacionInicialNoConfiguradoException.class)
    public ProblemDetail handleEstadoReservacionInicialNoConfigurado(EstadoReservacionInicialNoConfiguradoException ex) {
        // Falta seed de configuración del sistema → 503.
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Autenticación ─────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    // Intentos de login agotados en la ventana vigente → 429.
    @ExceptionHandler(LoginRateLimitExcedidoException.class)
    public ProblemDetail handleLoginRateLimitExcedido(LoginRateLimitExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Lector agotó el cupo de mensajes del chatbot en la ventana vigente → 429.
    @ExceptionHandler(ChatbotRateLimitExcedidoException.class)
    public ProblemDetail handleChatbotRateLimitExcedido(ChatbotRateLimitExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Sesión de chat inexistente o de otro usuario → 404.
    @ExceptionHandler(SesionChatNoEncontradaException.class)
    public ProblemDetail handleSesionChatNoEncontrada(SesionChatNoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Refresh token inválido, expirado o de usuario inexistente → 401.
    @ExceptionHandler(RefreshTokenInvalidoException.class)
    public ProblemDetail handleRefreshTokenInvalido(RefreshTokenInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // Cuenta bloqueada por multas pendientes → 423.
    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED,
                "Cuenta bloqueada por multas pendientes. Regularice su situación para continuar.");
    }

    // Cuenta inactiva o pendiente de verificación → 403.
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabled(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Cuenta inactiva o pendiente de verificación.");
    }

    // Autenticado sin el rol requerido para el método → 403.
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "No tiene permisos para realizar esta acción.");
    }

    // ── Validación ────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> detalles = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            detalles.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        problem.setProperty("errores", detalles);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleBodyMalformed(HttpMessageNotReadableException ex) {
        String detalle = Objects.toString(
                ex.getMostSpecificCause().getMessage(), "El cuerpo de la solicitud no es válido");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
    }

    // ── Recursos ──────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Ruta o recurso estático inexistente (ej. Swagger en perfil prod) → 404.
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    // ── Reglas de préstamo (cada motivo con su excepción) ─────

    @ExceptionHandler(PrestamoVencidoException.class)
    public ProblemDetail handlePrestamoVencido(PrestamoVencidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LimiteRenovacionesExcedidoException.class)
    public ProblemDetail handleLimiteRenovacionesExcedido(LimiteRenovacionesExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MaterialReservadoException.class)
    public ProblemDetail handleMaterialReservado(MaterialReservadoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Código de verificación incorrecto o expirado → 400.
    @ExceptionHandler(CodigoVerificacionInvalidoException.class)
    public ProblemDetail handleCodigoVerificacionInvalido(CodigoVerificacionInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Dependencia externa caída (Redis/SMTP) → 503.
    @ExceptionHandler(ServicioTemporalmenteNoDisponibleException.class)
    public ProblemDetail handleServicioTemporalmenteNoDisponible(ServicioTemporalmenteNoDisponibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // ── Acceso a datos ────────────────────────────────────────

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ProblemDetail handleSortInvalido(InvalidDataAccessApiUsageException ex) {
        log.warn("Parámetro de ordenamiento inválido", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parámetro de ordenamiento inválido: " + ex.getMessage());
    }

    // Dependencia de datos agotada o caída → 503.
    @ExceptionHandler({DataAccessResourceFailureException.class, UncategorizedDataAccessException.class})
    public ProblemDetail handleDataAccessResourceFailure(DataAccessException ex) {
        log.error("Fallo de acceso a dependencia de datos (Redis/BD) {}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        String root = Objects.toString(ex.getMostSpecificCause().getMessage(), ex.getMessage());
        String detail = "El servicio de almacenamiento temporal no está disponible. Intente más tarde."
                + (root != null ? " (" + root.substring(0, Math.min(200, root.length())) + ")" : "");
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
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

        String mensajeEx = "desconocida";
        if (ex != null) {
            mensajeEx = Objects.toString(ex.getMessage(), "sin mensaje");
        }
        log.error("Error no controlado en procedimiento almacenado: {}", mensajeEx, ex);
        String root = Objects.toString(ex.getMostSpecificCause().getMessage(), ex.getMessage());
        String detail = "Error interno del servidor: " + root.substring(0, Math.min(300, root.length()));
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    // Reenvía el status indicado por el servicio (400, 404, etc.).
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.getStatusCode().value()),
                ex.getReason() != null ? ex.getReason() : ex.getMessage()
        );
    }

    // ── Fallback ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerica(Exception ex) {
        log.error("Error no controlado: {}", ex.getMessage(), ex);
        String msg = ex.getMessage();
        String detail = "Error interno del servidor: " + (msg != null ? msg.substring(0, Math.min(300, msg.length())) : ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }
}