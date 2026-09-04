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
 * Fija SET LOCAL app.current_user_id antes de cada servicio transaccional
 * que modifica tablas auditadas. El trigger fn_auditoria_generica() lee
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
                            entityManager.createNativeQuery("SET LOCAL app.current_user_id = :id")
                                    .setParameter("id", u.getId().toString())
                                    .executeUpdate();
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        }
        return pjp.proceed();
    }
}
