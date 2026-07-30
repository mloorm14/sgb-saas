package com.uteq.backend.exception;

import com.uteq.backend.service.CorreoYaRegistradoException;
import com.uteq.backend.service.LoginRateLimitExcedidoException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Todas las respuestas de error se devuelven como {@link ProblemDetail}
 * (RFC 7807), requisito de la guía (A.1). Spring fija automáticamente el
 * status HTTP de la respuesta a partir de {@code ProblemDetail.getStatus()}
 * y serializa con Content-Type {@code application/problem+json}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ProblemDetail handleCorreoYaRegistrado(CorreoYaRegistradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    // OWASP A07 (Bloque C.2): LoginRateLimiter.estaBloqueado() -> AuthService
    // lanza esto ANTES de intentar autenticar, cuando correo+IP ya agotaron
    // los intentos fallidos permitidos en la ventana vigente. Ver
    // docs/mediciones/sec/2026-07-30-owasp-a07-fallo-identificacion-autenticacion.md
    // para el gap original (6/6 intentos devolvían 401 sin límite) que esto cierra.
    @ExceptionHandler(LoginRateLimitExcedidoException.class)
    public ProblemDetail handleLoginRateLimitExcedido(LoginRateLimitExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // UserDetailsServiceImpl marca accountLocked=true cuando
    // estados_usuario.nombre = BLOQUEADO_POR_MULTA. 423 Locked distingue
    // este caso de una contraseña incorrecta: el usuario necesita
    // regularizar su multa, no reintentar la clave.
    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED,
                "Cuenta bloqueada por multas pendientes. Regularice su situación para continuar.");
    }

    // UserDetailsServiceImpl marca disabled=true cuando estados_usuario.nombre
    // es INACTIVO o PENDIENTE_VERIFICACION.
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabled(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Cuenta inactiva o pendiente de verificación.");
    }

    // @PreAuthorize (método, AOP) lanza esto DESPUÉS de que el filtro de
    // Spring Security ya dejó pasar la request (usuario autenticado, pero
    // sin el rol requerido para ESTE método). Sin este handler caía en
    // handleGenerica -> 500, ocultando un 403 real de control de acceso
    // (hallazgo detectado al verificar en vivo TAREA 2 con una cuenta ADMIN
    // que no está en la lista de roles de LibroController.listar()).
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "No tiene permisos para realizar esta acción.");
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

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Los SPs con efectos secundarios (sp_crear_prestamo, sp_registrar_devolucion,
    // sp_pagar_multa, sp_anular_multa, sp_expirar_reservaciones_vencidas) lanzan
    // RAISE EXCEPTION ... USING ERRCODE = 'LBxxx' (ver docs/basedatos/CATALOGO-SP.md).
    // Postgres/JDBC no reconoce esos SQLSTATE custom, así que Hibernate los envuelve
    // en DataAccessException genérica -> sin este handler, caían todos a
    // handleGenerica -> 500, ocultando el error real de negocio (ej. "préstamo ya
    // devuelto" se veía como un 500 genérico en vez de un 409).
    @ExceptionHandler({
            InvalidDataAccessResourceUsageException.class,
            UncategorizedSQLException.class,
            DataAccessException.class
    })
    public ProblemDetail handleStoredProcedureError(Exception ex) {
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
                default -> null;
            };
            if (status != null) {
                return ProblemDetail.forStatusAndDetail(status, sqlEx.getMessage());
            }
        }

        log.error("Error no controlado en procedimiento almacenado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerica(Exception ex) {
        // Sin este log, un 500 no deja ningún rastro server-side: el cliente
        // recibe el detail genérico (correcto, no debe filtrar detalles
        // internos) pero el equipo no tiene forma de diagnosticar la causa.
        log.error("Error no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }
}