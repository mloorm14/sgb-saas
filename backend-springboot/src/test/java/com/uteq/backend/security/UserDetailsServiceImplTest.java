package com.uteq.backend.security;

import com.uteq.backend.entity.StatusUser;
import com.uteq.backend.entity.Role;
import com.uteq.backend.entity.User;
import com.uteq.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// Bloque C.4: UserDetailsServiceImpl real (no mockeado) contra un
// UsuarioRepository mockeado -- la unica dependencia externa real (BD).
// Antes de esto tenia 7.1% de cobertura porque AuthServiceTest no lo usa
// (usa AuthenticationManager mockeado directamente) y
// LibroControllerSecurityTest lo mockea con @MockitoBean.
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    private static final String CORREO = "userdetails-test@correo.com";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private User userWithStatus(String nameStatus, String... nombresRoles) {
        StatusUser status = new StatusUser();
        status.setId(1);
        status.setName(nameStatus);

        Set<Role> roles = new java.util.HashSet<>();
        int idSecuencia = 1;
        for (String nameRole : nombresRoles) {
            Role role = new Role();
            role.setId(idSecuencia++);
            role.setName(nameRole);
            roles.add(role);
        }

        return User.builder()
                .id(99L)
                .name("UserDetails")
                .lastName("De Prueba")
                .email(CORREO)
                .passwordHash("hash-bcrypt-de-prueba")
                .status(status)
                .emailVerified(true)
                .roles(roles)
                .dateRegistration(Instant.now())
                .updated(Instant.now())
                .build();
    }

    @Test
    void userActiveWithRoles_devuelveUserDetailsWithAuthoritiesRoleCorrectas() {
        User user = userWithStatus("ACTIVO", "LECTOR", "BIBLIOTECARIO");
        when(userRepository.findByEmail(CORREO)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertEquals(CORREO, userDetails.getUsername());
        assertEquals("hash-bcrypt-de-prueba", userDetails.getPassword());

        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_LECTOR", "ROLE_BIBLIOTECARIO"), authorities);

        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    void userNotFound_lanzaUsernameNotFoundException() {
        when(userRepository.findByEmail(CORREO)).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsServiceImpl.loadUserByUsername(CORREO));
        assertTrue(ex.getMessage().contains(CORREO));
    }

    // Verificado en vivo hace dias (usuario bloqueado por multa no puede
    // hacer login); queda como test permanente de regresion.
    @Test
    void userBlockedByFine_accountNonLockedEsFalseYSigueEnabled() {
        User user = userWithStatus("BLOQUEADO_POR_MULTA", "LECTOR");
        when(userRepository.findByEmail(CORREO)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    void userInactivo_disabledEsTrue() {
        User user = userWithStatus("INACTIVO", "LECTOR");
        when(userRepository.findByEmail(CORREO)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void userPendingVerification_disabledEsTrue() {
        User user = userWithStatus("PENDIENTE_VERIFICACION", "LECTOR");
        when(userRepository.findByEmail(CORREO)).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isEnabled());
    }
}
