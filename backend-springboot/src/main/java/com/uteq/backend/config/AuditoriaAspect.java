package com.uteq.backend.config;

import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Fija app.current_user_id (via set_config, equivalente a SET LOCAL)
 * antes de cada servicio transaccional que modifica tablas auditadas.
 * El trigger fn_auditoria_generica() lee
 * current_setting('app.current_user_id', true) en la misma transaccion/
 * conexion y deja de insertar usuario_id=NULL (bug "Sistema" robot).
 */
@Aspect
@Component
public class AuditoriaAspect {

    @PersistenceContext
    private EntityManager entityManager;

    private final UsuarioRepository usuarioRepository;

    public AuditoriaAspect(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Around("@annotation(tx)")
    public Object setCurrentUser(ProceedingJoinPoint pjp, org.springframework.transaction.annotation.Transactional tx) throws Throwable {
        if (!tx.readOnly()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String correo = auth.getName();
                if (correo != null && correo.contains("@")) {
                    usuarioRepository.findByCorreo(correo).ifPresent(u -> {
                        try {
                            // set_config() en vez de SET LOCAL: PostgreSQL/JDBC
                            // no admite bind parameters ($1) en sentencias
                            // utilitarias como SET LOCAL ("syntax error at
                            // near $1" -> 503, ver fix ea1847f). Al ser
                            // llamada a funcion, set_config si acepta :id.
                            entityManager.createNativeQuery(
                                            "SELECT set_config('app.current_user_id', CAST(:id AS text), true)")
                                    .setParameter("id", u.getId().toString())
                                    .getSingleResult();
                        } catch (Exception ignored) {
                            // best-effort: si falla set_config, la operación
                            // de negocio continúa y el trigger registra NULL
                        }
                    });
                }
            }
        }
        return pjp.proceed();
    }
}
