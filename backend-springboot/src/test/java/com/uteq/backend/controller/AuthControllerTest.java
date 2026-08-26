package com.uteq.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.LoginRequestDTO;
import com.uteq.backend.dto.RegistroRequestDTO;
import com.uteq.backend.dto.TokenResponseDTO;
import com.uteq.backend.dto.UsuarioResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.AuthService;
import com.uteq.backend.service.CorreoYaRegistradoException;
import com.uteq.backend.service.LoginRateLimitExcedidoException;
import com.uteq.backend.service.RefreshTokenInvalidoException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Bloque C.4: mismo patron que LibroControllerSecurityTest -- AuthController
// real via MockMvc + springSecurity() + JwtAuthFilter real, en vez de solo
// tests unitarios de AuthService con el controller sin ejercitar. Esto
// ademas hace que las excepciones lanzadas por AuthService pasen de verdad
// por GlobalExceptionHandler (antes solo se verificaba con assertThrows
// a nivel de servicio, nunca se llegaba al @RestControllerAdvice real).
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void registro_datosValidos_devuelve201ConUsuarioCreado() throws Exception {
        RegistroRequestDTO dto = new RegistroRequestDTO("Nueva", "Persona", "nueva@uteq.edu.ec", "password123");
        when(authService.registrar(any())).thenReturn(
                new UsuarioResponseDTO(1L, "Nueva", "nueva@uteq.edu.ec", List.of("LECTOR")));

        mockMvc.perform(post("/api/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("nueva@uteq.edu.ec"));
    }

    @Test
    void registro_correoDuplicado_devuelve409ProblemDetail() throws Exception {
        RegistroRequestDTO dto = new RegistroRequestDTO("Nueva", "Persona", "duplicado@uteq.edu.ec", "password123");
        when(authService.registrar(any()))
                .thenThrow(new CorreoYaRegistradoException("El correo ya está registrado: duplicado@uteq.edu.ec"));

        mockMvc.perform(post("/api/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ya está registrado")));
    }

    @Test
    void login_credencialesValidas_devuelve200ConCookieRefreshTokenHttpOnly() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("valido@uteq.edu.ec", "password123");
        when(authService.login(any(), anyString()))
                .thenReturn(new TokenResponseDTO("access-token-x", "refresh-token-y", 3600));
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-x"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-token-y")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=None")));
    }

    @Test
    void login_credencialesInvalidas_devuelve401ProblemDetail() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("valido@uteq.edu.ec", "claveMala");
        when(authService.login(any(), anyString())).thenThrow(new BadCredentialsException("Credenciales inválidas"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_rateLimitExcedido_devuelve429ProblemDetail() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("valido@uteq.edu.ec", "password123");
        when(authService.login(any(), anyString()))
                .thenThrow(new LoginRateLimitExcedidoException("Demasiados intentos fallidos. Intente en 600 segundos."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests());
    }

    // Bloque C.4 (TAREA 5): GlobalExceptionHandler.handleLocked/handleDisabled
    // no tenian ningun test que los ejercitara de verdad -- el estado
    // BLOQUEADO_POR_MULTA/INACTIVO ya se verifico en vivo y tiene test
    // permanente a nivel de UserDetailsServiceImplTest, pero la traduccion a
    // 423/403 con ProblemDetail solo ocurria en produccion, nunca en un test.
    @Test
    void login_cuentaBloqueadaPorMulta_devuelve423ProblemDetail() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("bloqueado@uteq.edu.ec", "password123");
        when(authService.login(any(), anyString()))
                .thenThrow(new LockedException("Cuenta bloqueada por multas pendientes"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(containsString("multas")));
    }

    @Test
    void login_cuentaInactiva_devuelve403ProblemDetail() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("inactivo@uteq.edu.ec", "password123");
        when(authService.login(any(), anyString()))
                .thenThrow(new DisabledException("Cuenta inactiva"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("inactiva")));
    }

    // Bloque C.4 (TAREA 5): GlobalExceptionHandler.handleValidation tampoco
    // tenia ningun test -- @Valid en RegistroRequestDTO ya existia, pero
    // nada ejercitaba el camino real de "datos invalidos -> 400 con mapa de
    // errores por campo".
    @Test
    void registro_datosInvalidos_devuelve400ConErroresPorCampo() throws Exception {
        RegistroRequestDTO dto = new RegistroRequestDTO("", "", "no-es-un-correo", "corta");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists())
                .andExpect(jsonPath("$.errores.password").exists());
    }

    // REQ-F-001 criterio 3: registro_datosInvalidos_devuelve400ConErroresPorCampo
    // invalida los 4 campos a la vez, asi que un futuro relajamiento de la
    // validacion de contraseña seguiria devolviendo 400 igual (los otros 3
    // campos ya bastan). Este test aisla el criterio: solo la contraseña es
    // invalida, el resto de campos son validos.
    @Test
    void registro_passwordCorta_devuelve400ProblemDetail() throws Exception {
        RegistroRequestDTO dto = new RegistroRequestDTO("Nueva", "Persona", "nueva@uteq.edu.ec", "corta");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.password").exists())
                .andExpect(jsonPath("$.errores.nombre").doesNotExist())
                .andExpect(jsonPath("$.errores.apellido").doesNotExist())
                .andExpect(jsonPath("$.errores.correo").doesNotExist());
    }

    @Test
    void refresh_sinCookie_devuelve400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("refreshToken")));
    }

    @Test
    void refresh_cookieInvalida_devuelve401ProblemDetail() throws Exception {
        when(authService.refresh(eq("token-malo")))
                .thenThrow(new RefreshTokenInvalidoException("Refresh token inválido o expirado. Inicie sesión nuevamente."));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "token-malo")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_cookieValida_devuelve200ConNuevoAccessToken() throws Exception {
        when(authService.refresh(eq("token-bueno")))
                .thenReturn(new TokenResponseDTO("nuevo-access-token", "token-bueno", 3600));
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", "token-bueno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("nuevo-access-token"));
    }

    @Test
    @WithMockUser
    void logout_autenticado_devuelve204YLimpiaCookieRefreshToken() throws Exception {
        doNothing().when(authService).logout(anyString(), anyString());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer cualquier-valor"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logout_sinAutenticar_esRechazado() throws Exception {
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer cualquier-valor"))
                .andExpect(status().is4xxClientError());
    }
}
