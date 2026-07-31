package com.uteq.backend.security;

import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.UsuarioRepository;
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

    private static final String CORREO = "userdetails-test@uteq.edu.ec";

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private Usuario usuarioConEstado(String nombreEstado, String... nombresRoles) {
        EstadoUsuario estado = new EstadoUsuario();
        estado.setId(1);
        estado.setNombre(nombreEstado);

        Set<Rol> roles = new java.util.HashSet<>();
        int idSecuencia = 1;
        for (String nombreRol : nombresRoles) {
            Rol rol = new Rol();
            rol.setId(idSecuencia++);
            rol.setNombre(nombreRol);
            roles.add(rol);
        }

        return Usuario.builder()
                .id(99L)
                .nombre("UserDetails")
                .apellido("De Prueba")
                .correo(CORREO)
                .passwordHash("hash-bcrypt-de-prueba")
                .estado(estado)
                .correoVerificado(true)
                .roles(roles)
                .fechaRegistro(Instant.now())
                .actualizadoEn(Instant.now())
                .build();
    }

    @Test
    void usuarioActivoConRoles_devuelveUserDetailsConAuthoritiesRoleCorrectas() {
        Usuario usuario = usuarioConEstado("ACTIVO", "LECTOR", "BIBLIOTECARIO");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

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
    void usuarioNoEncontrado_lanzaUsernameNotFoundException() {
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsServiceImpl.loadUserByUsername(CORREO));
        assertTrue(ex.getMessage().contains(CORREO));
    }

    // Verificado en vivo hace dias (usuario bloqueado por multa no puede
    // hacer login); queda como test permanente de regresion.
    @Test
    void usuarioBloqueadoPorMulta_accountNonLockedEsFalseYSigueHabilitado() {
        Usuario usuario = usuarioConEstado("BLOQUEADO_POR_MULTA", "LECTOR");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isAccountNonLocked());
        assertTrue(userDetails.isEnabled());
    }

    @Test
    void usuarioInactivo_disabledEsTrue() {
        Usuario usuario = usuarioConEstado("INACTIVO", "LECTOR");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void usuarioPendienteVerificacion_disabledEsTrue() {
        Usuario usuario = usuarioConEstado("PENDIENTE_VERIFICACION", "LECTOR");
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(CORREO);

        assertFalse(userDetails.isEnabled());
    }
}
