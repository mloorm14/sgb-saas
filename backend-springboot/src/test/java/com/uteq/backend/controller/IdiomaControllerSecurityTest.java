package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.entity.Idioma;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cubre el hueco de rol reportado en la auditoría del Dr. Guerrero (A23,
// nota 1): POST /api/v1/idiomas no tenía ningún @PreAuthorize, permitiendo
// que cualquier autenticado (incluido LECTOR) creara idiomas.
@WebMvcTest(IdiomaController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class IdiomaControllerSecurityTest {

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
    private IdiomaRepository idiomaRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final String CUERPO_VALIDO = "{\"nombre\":\"Nuevo Idioma\"}";

    private Idioma idiomaGuardado() {
        Idioma i = new Idioma();
        i.setId(1);
        i.setNombre("Nuevo Idioma");
        i.setCodigo("nue");
        return i;
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void crear_conRolGerente_sePermite() throws Exception {
        when(idiomaRepository.existsByCodigoIgnoreCase(anyString())).thenReturn(false);
        when(idiomaRepository.save(any())).thenReturn(idiomaGuardado());

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void crear_conRolAdmin_sePermite() throws Exception {
        when(idiomaRepository.existsByCodigoIgnoreCase(anyString())).thenReturn(false);
        when(idiomaRepository.save(any())).thenReturn(idiomaGuardado());

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void crear_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void crear_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }
}
