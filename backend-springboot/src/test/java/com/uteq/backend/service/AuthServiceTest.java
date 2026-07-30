package com.uteq.backend.service;

import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.RegistroRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.entity.EstadoUsuario;
import com.uteq.backend.entity.Rol;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
import com.uteq.backend.repository.EstadoUsuarioRepository;
import com.uteq.backend.repository.RolRepository;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP_DE_PRUEBA = "10.0.0.1";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private EstadoUsuarioRepository estadoUsuarioRepository;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private BitacoraAuditoriaRepository bitacoraAuditoriaRepository;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioDePrueba() {
        Instant ahora = Instant.now();

        EstadoUsuario activo = new EstadoUsuario();
        activo.setId(1);
        activo.setNombre("ACTIVO");

        Rol lector = new Rol();
        lector.setId(1);
        lector.setNombre("LECTOR");

        return Usuario.builder()
                .id(1L)
                .nombre("Lector de Prueba")
                .apellido("Apellido de Prueba")
                .correo("lector@uteq.edu.ec")
                .passwordHash("hash-encriptado")
                .estado(activo)
                .correoVerificado(true)
                .roles(Set.of(lector))
                .fechaRegistro(ahora)
                .actualizadoEn(ahora)
                .build();
    }

    @Test
    void loginExitoso() {
        Usuario usuario = usuarioDePrueba();
        LoginRequestDTO dto = new LoginRequestDTO("lector@uteq.edu.ec", "password123");

        when(usuarioRepository.findByCorreo("lector@uteq.edu.ec")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("access-token-de-prueba");
        when(jwtService.generateRefreshToken(usuario)).thenReturn("refresh-token-de-prueba");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        TokenResponseDTO resultado = authService.login(dto, IP_DE_PRUEBA);

        assertNotNull(resultado);
        assertNotNull(resultado.accessToken());
        assertFalse(resultado.accessToken().isBlank());
        assertNotNull(resultado.refreshToken());
        assertFalse(resultado.refreshToken().isBlank());
        assertEquals(3600L, resultado.expiresIn());
        assertEquals("Bearer", resultado.tokenType());
    }

    // OWASP A07: un login exitoso debe resetear el contador de intentos
    // fallidos de esa combinación correo+IP -- si no, un usuario que se
    // equivocó una vez y luego acertó seguiría acumulando hacia el bloqueo.
    @Test
    void loginExitosoReseteaContadorDeRateLimit() {
        Usuario usuario = usuarioDePrueba();
        LoginRequestDTO dto = new LoginRequestDTO("lector@uteq.edu.ec", "password123");

        when(usuarioRepository.findByCorreo("lector@uteq.edu.ec")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("access-token-de-prueba");
        when(jwtService.generateRefreshToken(usuario)).thenReturn("refresh-token-de-prueba");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        authService.login(dto, IP_DE_PRUEBA);

        verify(loginRateLimiter).resetear("lector@uteq.edu.ec", IP_DE_PRUEBA);
        verify(bitacoraAuditoriaRepository).save(any());
    }

    @Test
    void loginClaveIncorrecta() {
        LoginRequestDTO dto = new LoginRequestDTO("lector@uteq.edu.ec", "claveIncorrecta");

        doThrow(new BadCredentialsException("Credenciales inválidas"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(dto, IP_DE_PRUEBA));
    }

    // OWASP A07: cada intento fallido debe incrementar el contador de esa
    // combinación correo+IP -- sin esto, LoginRateLimiter.estaBloqueado()
    // nunca llegaría al máximo configurado.
    @Test
    void loginFallidoIncrementaContadorDeRateLimit() {
        LoginRequestDTO dto = new LoginRequestDTO("lector@uteq.edu.ec", "claveIncorrecta");

        doThrow(new BadCredentialsException("Credenciales inválidas"))
                .when(authenticationManager).authenticate(any());

        assertThrows(BadCredentialsException.class, () -> authService.login(dto, IP_DE_PRUEBA));

        verify(loginRateLimiter).registrarFallo("lector@uteq.edu.ec", IP_DE_PRUEBA);
        verify(bitacoraAuditoriaRepository).save(any());
    }

    // OWASP A07: el escenario del 6to intento -- LoginRateLimiter ya
    // reporta la combinación correo+IP como bloqueada (equivalente a un
    // contador que llegó al máximo configurado, ver LoginRateLimiterTest
    // para el límite en sí). AuthService debe responder con la excepción
    // que GlobalExceptionHandler traduce a 429 SIN llamar a
    // authenticationManager.authenticate() -- ni siquiera se intenta
    // autenticar contra credenciales potencialmente correctas.
    @Test
    void loginBloqueadoPorRateLimitNoIntentaAutenticar() {
        LoginRequestDTO dto = new LoginRequestDTO("lector@uteq.edu.ec", "password123");

        when(loginRateLimiter.estaBloqueado("lector@uteq.edu.ec", IP_DE_PRUEBA)).thenReturn(true);
        when(loginRateLimiter.segundosRestantes("lector@uteq.edu.ec", IP_DE_PRUEBA)).thenReturn(600L);

        assertThrows(LoginRateLimitExcedidoException.class, () -> authService.login(dto, IP_DE_PRUEBA));

        verify(authenticationManager, never()).authenticate(any());
        verify(loginRateLimiter, never()).registrarFallo(any(), any());
    }

    @Test
    void registroCorreoDuplicado() {
        Usuario usuarioExistente = usuarioDePrueba();
        RegistroRequestDTO dto = new RegistroRequestDTO(
                "Nuevo Lector", "Apellido Nuevo", "lector@uteq.edu.ec", "password123"
        );

        when(usuarioRepository.findByCorreo("lector@uteq.edu.ec")).thenReturn(Optional.of(usuarioExistente));

        assertThrows(CorreoYaRegistradoException.class, () -> authService.registrar(dto));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void logoutGuardaTokenEnBlacklist() {
        String token = "token-de-prueba";
        String jti = "550e8400-e29b-41d4-a716-446655440000";
        Date expiracionFutura = new Date(System.currentTimeMillis() + 3600000);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.extractJti(token)).thenReturn(jti);
        when(jwtService.extractExpiration(token)).thenReturn(expiracionFutura);

        authService.logout(token, IP_DE_PRUEBA);

        verify(valueOperations).set(eq("blacklist:" + jti), eq("revoked"), anyLong(), eq(TimeUnit.SECONDS));
        verify(bitacoraAuditoriaRepository).save(any());
    }

    @Test
    void refreshConTokenValido() {
        String refreshTokenDePrueba = "refresh-token-de-prueba";
        Usuario usuario = usuarioDePrueba();

        when(jwtService.validateToken(refreshTokenDePrueba)).thenReturn(true);
        when(jwtService.extractCorreo(refreshTokenDePrueba)).thenReturn(usuario.getCorreo());
        when(usuarioRepository.findByCorreo(usuario.getCorreo())).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("nuevo-access-token");

        TokenResponseDTO resultado = authService.refresh(refreshTokenDePrueba);

        assertEquals("nuevo-access-token", resultado.accessToken());
        assertSame(refreshTokenDePrueba, resultado.refreshToken());
    }
}
