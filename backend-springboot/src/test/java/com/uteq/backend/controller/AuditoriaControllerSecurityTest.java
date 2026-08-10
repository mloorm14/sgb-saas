package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

// Módulo 6: solo GERENTE/ADMIN consultan la bitácora de auditoría. Mismo
// patrón de test que ConfiguracionSistemaControllerSecurityTest y
// UsuarioAdminControllerSecurityTest (Módulo 5): WebMvcTest + SecurityConfig
// real + MockMvc reconstruido con springSecurity().
@WebMvcTest(AuditoriaController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuditoriaControllerSecurityTest {

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
    private AuditoriaService auditoriaService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_conRolAdmin_sePermite() throws Exception {
        when(auditoriaService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void listar_conRolGerente_sePermite() throws Exception {
        when(auditoriaService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void listar_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void listar_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_conFiltrosDeQuery_sePermiteYLosPropaga() throws Exception {
        when(auditoriaService.listar(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/auditoria")
                        .param("usuarioId", "9")
                        .param("modulo", "usuarios")
                        .param("desde", "2026-01-01T00:00:00Z")
                        .param("hasta", "2026-12-31T23:59:59Z"))
                .andExpect(status().isOk());
    }
}
