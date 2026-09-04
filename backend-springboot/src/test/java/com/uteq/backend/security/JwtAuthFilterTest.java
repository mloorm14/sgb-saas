package com.uteq.backend.security;

import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Bloque C.4: filtro real (no mockeado, ver lección de LibroControllerSecurityTest
// -- mockear JwtAuthFilter con @MockitoBean rompe la cadena silenciosamente).
// JwtService se usa real (ya cubierto por JwtServiceTest) para generar tokens
// de verdad; RedisTemplate y UserDetailsServiceImpl se mockean porque son
// las dependencias externas reales (Redis/BD), consistente con que
// UserDetailsServiceImpl ya tiene su propio test dedicado.
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "CAMBIAR_EN_PRODUCCION_MIN_256_BITS";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void construirFiltro() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMs", 604_800_000L);

        filter = new JwtAuthFilter(jwtService, redisTemplate, userDetailsServiceImpl);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuarioDePrueba() {
        EstadoUsuario activo = new EstadoUsuario();
        activo.setId(1);
        activo.setNombre("ACTIVO");

        Rol lector = new Rol();
        lector.setId(1);
        lector.setNombre("LECTOR");

        return Usuario.builder()
                .id(7L)
                .nombre("Filtro")
                .apellido("De Prueba")
                .correo("filtro-test@correo.com")
                .passwordHash("hash")
                .estado(activo)
                .correoVerificado(true)
                .roles(Set.of(lector))
                .fechaRegistro(Instant.now())
                .actualizadoEn(Instant.now())
                .build();
    }

    @Test
    void sinHeaderAuthorization_continuaSinAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void headerConTokenValidoYNoEnBlacklist_pueblaContextoDeSeguridad() throws Exception {
        Usuario usuario = usuarioDePrueba();
        String token = jwtService.generateToken(usuario);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        UserDetails userDetails = User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .roles("LECTOR")
                .build();
        when(userDetailsServiceImpl.loadUserByUsername(usuario.getCorreo())).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication != null && authentication.isAuthenticated());
        assertTrue(authentication.getPrincipal() == userDetails);
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertTrue(authorities.contains("ROLE_LECTOR"));
    }

    @Test
    void headerConTokenInvalido_continuaSinAutenticarYNoConsultaRedisNiBd() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer esto-no-es-un-jwt-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(redisTemplate, never()).hasKey(anyString());
        verify(userDetailsServiceImpl, never()).loadUserByUsername(any());
    }

    @Test
    void headerConTokenEnBlacklist_noAutenticaYNoConsultaUserDetails() throws Exception {
        Usuario usuario = usuarioDePrueba();
        String token = jwtService.generateToken(usuario);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsServiceImpl, never()).loadUserByUsername(any());
    }

    // Ejercita el catch(Exception) de doFilterInternal: token
    // estructuralmente valido pero el correo ya no resuelve a un usuario
    // (cuenta eliminada entre la emision del token y esta request).
    @Test
    void headerConTokenValidoPeroUsuarioNoEncontrado_limpiaContextoYContinua() throws Exception {
        Usuario usuario = usuarioDePrueba();
        String token = jwtService.generateToken(usuario);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userDetailsServiceImpl.loadUserByUsername(usuario.getCorreo()))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // Fail-closed ante caida de Redis (decision OWASP A07 documentada en
    // docs/mediciones/sec/owasp/decision-fail-closed-jwt-redis.md): si la
    // consulta a la blacklist falla, no se trata el token como vigente --
    // se rechaza la request con 401 sin continuar la cadena. Con el
    // comportamiento anterior (fail-open) este mismo escenario dejaba
    // pasar tokens revocados durante el corte.
    @Test
    void headerConTokenValidoYRedisCaido_rechazaCon401SinContinuarCadena() throws Exception {
        Usuario usuario = usuarioDePrueba();
        String token = jwtService.generateToken(usuario);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis caido"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsServiceImpl, never()).loadUserByUsername(any());
    }
}
