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
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.UncategorizedDataAccessException;
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
        // 503: es un error de configuración del sistema (falta seed), no del cliente
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
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

    // Módulo H (chatbot): ChatbotService.chatbotRateLimiter.estaBloqueado()
    // -> excepción cuando el LECTOR agotó el cupo de mensajes al asistente
    // en la ventana vigente. Mismo contrato que handleLoginRateLimitExcedido.
    @ExceptionHandler(ChatbotRateLimitExcedidoException.class)
    public ProblemDetail handleChatbotRateLimitExcedido(ChatbotRateLimitExcedidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    // Módulo H (chatbot): sesión inexistente o de otro usuario (un LECTOR
    // solo accede a sus propias sesiones). Mismo contrato que handleNotFound.
    @ExceptionHandler(SesionChatNoEncontradaException.class)
    public ProblemDetail handleSesionChatNoEncontrada(SesionChatNoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // AuthService.refresh() lanza esto cuando el refreshToken de la cookie
    // es invalido/expirado (JwtService.validateToken devuelve false) o
    // cuando el correo que codifica ya no resuelve a un usuario existente.
    // Antes de este handler, ambos casos eran una RuntimeException sin
    // @ExceptionHandler dedicado, caian en handleGenerica y respondian 500
    // -- ocultando lo que en realidad es una sesion no autorizada (401),
    // no un error interno del servidor. Mismo mensaje generico en ambos
    // casos (no distingue "token invalido" de "usuario no encontrado")
    // para no filtrar informacion sobre si una cuenta existe.
    @ExceptionHandler(RefreshTokenInvalidoException.class)
    public ProblemDetail handleRefreshTokenInvalido(RefreshTokenInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
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

    // Spring la lanza cuando no existe ningún recurso estático/ruta para el
    // path pedido (ej. /swagger-ui.html o /api/docs con el perfil prod, que
    // los deshabilita vía springdoc.*.enabled=false). Sin este handler caía
    // en handleGenerica -> 500, ocultando lo que en realidad es un 404
    // (ver docs/mediciones/sec/2026-08-10-owasp-a05-fix-csp-stacktrace-swagger-nonroot.md,
    // verificación pendiente que detectó este 500 al probarlo en Docker real).
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    // PrestamoService.renovar(): las 3 reglas de negocio que bloquean una
    // renovación, cada una como su propia excepción para que el cliente
    // pueda distinguir el motivo exacto del 409 sin parsear el mensaje.
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

    // Módulo 9.5: código de 6 dígitos incorrecto o ya expirado en Redis.
    // 400 (no 401/403): no es un problema de autenticación ni de permisos,
    // es un dato de entrada que no coincide con lo esperado.
    @ExceptionHandler(CodigoVerificacionInvalidoException.class)
    public ProblemDetail handleCodigoVerificacionInvalido(CodigoVerificacionInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // VerificacionCorreoService.generarYEnviarCodigo() cuando Redis no
    // permite GUARDAR el código (dependencia caída o sin cuota). 503, no 500:
    // el registro no debe "tener éxito" con un código que no existirá en
    // Redis -- el usuario quedaría atrapado en PENDIENTE_VERIFICACION sin
    // poder completar el alta. Ver
    // docs/mediciones/sec/2026-08-14-incidente-500-auth-redis-produccion.md.
    @ExceptionHandler(ServicioTemporalmenteNoDisponibleException.class)
    public ProblemDetail handleServicioTemporalmenteNoDisponible(ServicioTemporalmenteNoDisponibleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // Red de seguridad para cualquier acceso a datos que NO esté envuelto en
    // degradación local (ej. @Cacheable("libros"/"sugerencias-libros") de
    // LibroService con Redis/Upstash caído): las fallas de conexión
    // (DataAccessResourceFailureException) y las de protocolo/error RESP de
    // Redis (UncategorizedDataAccessException, ej. cuota de comandos agotada
    // en Upstash) deben responder 503 Service Unavailable, no el 500
    // genérico -- es una dependencia agotada/caída, no un bug del código.
    // Antes de este handler, ambas caían en handleStoredProcedureError -> 500
    // con el mensaje engañoso "Error no controlado en procedimiento
    // almacenado" (ver docs/mediciones/sec/2026-08-14-incidente-500-auth-redis-produccion.md).
    @ExceptionHandler({DataAccessResourceFailureException.class, UncategorizedDataAccessException.class})
    public ProblemDetail handleDataAccessResourceFailure(DataAccessException ex) {
        log.error("Fallo de acceso a dependencia de datos (Redis/BD)", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de almacenamiento temporal no está disponible. Intente más tarde.");
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