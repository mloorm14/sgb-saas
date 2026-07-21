package com.uteq.backend.exception;

import com.uteq.backend.service.CorreoYaRegistradoException;
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

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenerica(Exception ex) {
        // Sin este log, un 500 no deja ningún rastro server-side: el cliente
        // recibe el detail genérico (correcto, no debe filtrar detalles
        // internos) pero el equipo no tiene forma de diagnosticar la causa.
        log.error("Error no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }
}
