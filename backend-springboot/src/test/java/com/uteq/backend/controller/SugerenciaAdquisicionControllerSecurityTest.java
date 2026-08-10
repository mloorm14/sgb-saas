package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.CambioEstadoSugerenciaRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionRequestDTO;
import com.uteq.backend.dto.SugerenciaAdquisicionResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.SugerenciaAdquisicionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Módulo 9.3 del roadmap: LECTOR crea/ve las suyas, GERENTE/ADMIN listan
// todas y cambian estado. Mismo patrón que
// ConfiguracionSistemaControllerSecurityTest/LibroControllerSecurityTest --
// WebMvcTest + SecurityConfig real + MockMvc reconstruido con
// springSecurity().
@WebMvcTest(SugerenciaAdquisicionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SugerenciaAdquisicionControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void construirMockMvcConSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SugerenciaAdquisicionService sugerenciaService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private SugerenciaAdquisicionRequestDTO requestValido() {
        return new SugerenciaAdquisicionRequestDTO(
                "Clean Architecture", "Robert C. Martin", null, "Complementa el material de POO");
    }

    private SugerenciaAdquisicionResponseDTO responseCreada() {
        return new SugerenciaAdquisicionResponseDTO(
                1L, 7L, "Clean Architecture", "Robert C. Martin", null,
                "Complementa el material de POO", "PENDIENTE", null, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void crear_conRolLector_sePermite() throws Exception {
        when(sugerenciaService.crear(any(), any())).thenReturn(responseCreada());

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isCreated());
    }

    // Regresion: BIBLIOTECARIO no es LECTOR ni GERENTE/ADMIN -- no debería
    // poder sugerir adquisiciones en nombre propio (ese flujo es del
    // lector) ni gestionarlas (eso es del gerente).
    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void crear_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void listarTodas_conRolGerente_sePermite() throws Exception {
        when(sugerenciaService.listarTodas(anyString(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion").param("estado", "PENDIENTE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void listarTodas_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/sugerencias-adquisicion"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void cambiarEstado_conRolGerente_sePermite() throws Exception {
        when(sugerenciaService.cambiarEstado(anyLong(), anyString(), any())).thenReturn(responseCreada());

        mockMvc.perform(patch("/api/v1/sugerencias-adquisicion/1/estado")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CambioEstadoSugerenciaRequestDTO("APROBADA"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void cambiarEstado_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(patch("/api/v1/sugerencias-adquisicion/1/estado")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CambioEstadoSugerenciaRequestDTO("APROBADA"))))
                .andExpect(status().isForbidden());
    }
}
