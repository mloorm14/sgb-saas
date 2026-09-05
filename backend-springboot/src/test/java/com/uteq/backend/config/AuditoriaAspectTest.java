package com.uteq.backend.config;

import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresión del fix ea1847f: el aspecto debe fijar el usuario de
 * auditoría vía set_config() parametrizado (SET LOCAL no admite
 * bind parameters $1 en PostgreSQL/JDBC -> 503).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaAspectTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private Query query;

    private AuditoriaAspect aspect;

    @org.junit.jupiter.api.BeforeEach
    void cablearAspecto() {
        // Cableado explícito: EntityManager va por @PersistenceContext
        // (sin setter ni constructor), se inyecta por reflexión.
        aspect = new AuditoriaAspect(usuarioRepository);
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
    }

    private Transactional txLecturaEscritura() {
        Transactional tx = mock(Transactional.class);
        when(tx.readOnly()).thenReturn(false);
        return tx;
    }

    private void autenticarComo(String correo) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("principal-mock");
        when(auth.getName()).thenReturn(correo);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("ejecuta set_config con el id del usuario autenticado y continua el joinpoint")
    void ejecutaSetConfigConId() throws Throwable {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRepository.findByCorreo("auditor@correo.com")).thenReturn(Optional.of(usuario));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn("7");
        when(pjp.proceed()).thenReturn("ok");
        autenticarComo("auditor@correo.com");

        Object resultado = aspect.setCurrentUser(pjp, txLecturaEscritura());

        assertEquals("ok", resultado);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("set_config('app.current_user_id'"),
                "Debe usar set_config, no SET LOCAL: " + sql.getValue());
        verify(query).setParameter(eq("id"), eq("7"));
        verify(query).getSingleResult();
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("no toca la BD en transacciones de solo lectura")
    void omiteTransaccionSoloLectura() throws Throwable {
        Transactional txSoloLectura = mock(Transactional.class);
        when(txSoloLectura.readOnly()).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");

        aspect.setCurrentUser(pjp, txSoloLectura);

        verify(entityManager, never()).createNativeQuery(anyString());
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("no toca la BD sin autenticacion")
    void omiteSinAutenticacion() throws Throwable {
        when(pjp.proceed()).thenReturn("ok");

        aspect.setCurrentUser(pjp, txLecturaEscritura());

        verify(entityManager, never()).createNativeQuery(anyString());
        verify(pjp).proceed();
    }
}
