package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.entity.Language;
import com.uteq.backend.repository.LanguageRepository;
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
@WebMvcTest(LanguageController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class LanguageControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void construirMockMvcWithSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @MockitoBean
    private LanguageRepository languageRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private static final String CUERPO_VALIDO = "{\"nombre\":\"Nuevo Idioma\"}";

    private Language languageGuardado() {
        Language i = new Language();
        i.setId(1);
        i.setName("Nuevo Idioma");
        i.setCode("nue");
        return i;
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void create_withRoleManager_sePermite() throws Exception {
        when(languageRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(languageRepository.save(any())).thenReturn(languageGuardado());

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withRoleAdmin_sePermite() throws Exception {
        when(languageRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(languageRepository.save(any())).thenReturn(languageGuardado());

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void create_withRoleLibrarian_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }
}
