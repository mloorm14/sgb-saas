package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Mismo patrón que ConfiguracionSistemaControllerSecurityTest/
// LibroControllerSecurityTest: WebMvcTest + SecurityConfig real + MockMvc
// reconstruido con springSecurity(). A diferencia de ConfiguracionSistema
// (solo ADMIN), acá los 4 roles pueden entrar -- la restricción real
// ("propio vs cualquiera" para LECTOR) vive en NotificacionService, no en
// el @PreAuthorize del controller, así que no se prueba aquí (ver
// NotificacionServiceTest).
@WebMvcTest(NotificacionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class NotificacionControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @MockitoBean
    private NotificacionService notificacionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "LECTOR")
    void listarPorUsuario_conRolLector_sePermite() throws Exception {
        when(notificacionService.listarPorUsuario(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notificaciones/usuario/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void listarPorUsuario_conRolBibliotecario_sePermite() throws Exception {
        when(notificacionService.listarPorUsuario(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notificaciones/usuario/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void listarPorUsuario_conRolGerente_sePermite() throws Exception {
        when(notificacionService.listarPorUsuario(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notificaciones/usuario/1"))
                .andExpect(status().isOk());
    }

    // Regresión: un rol sin relación con notificaciones (ninguno definido
    // en este sistema queda fuera de los 4 permitidos, pero se deja el
    // caso explícito para que una futura ampliación de roles no olvide
    // revisar este endpoint) -- mismo criterio de "regresión" que
    // LibroControllerSecurityTest#crear_conRolLector_sigueRechazado.
    @Test
    @WithMockUser(roles = "ADMIN")
    void listarPorUsuario_conRolAdmin_sePermite() throws Exception {
        when(notificacionService.listarPorUsuario(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notificaciones/usuario/1"))
                .andExpect(status().isOk());
    }
}
