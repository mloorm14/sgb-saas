package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.ConfiguracionSistemaRequestDTO;
import com.uteq.backend.dto.ConfiguracionSistemaResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.ConfiguracionSistemaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Modulo 9.4: solo ADMIN puede leer/editar parametros del sistema (ni
// siquiera GERENTE -- ver Modulo 5.3 del roadmap sobre la separacion
// ADMIN=permisos/parametros vs GERENTE=operacion diaria). Mismo patron de
// test que LibroControllerSecurityTest: WebMvcTest + SecurityConfig real +
// MockMvc reconstruido con springSecurity() para que @WithMockUser puebla
// el SecurityContext de verdad.
@WebMvcTest(ConfiguracionSistemaController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ConfiguracionSistemaControllerSecurityTest {

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
    private ConfiguracionSistemaService configuracionSistemaService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listar_conRolAdmin_sePermite() throws Exception {
        when(configuracionSistemaService.listar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void actualizar_conRolAdmin_sePermite() throws Exception {
        when(configuracionSistemaService.actualizar(anyString(), anyString()))
                .thenReturn(new ConfiguracionSistemaResponseDTO("dias_prestamo_default", "20"));

        mockMvc.perform(put("/api/v1/configuracion/dias_prestamo_default")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConfiguracionSistemaRequestDTO("20"))))
                .andExpect(status().isOk());
    }

    // Regresion: GERENTE administra operacion diaria pero NO parametros
    // del sistema -- distinto del resto de paneles admin (Auditoria/
    // Reportes en Modulo 5-7, donde GERENTE si tiene acceso).
    @Test
    @WithMockUser(roles = "GERENTE")
    void listar_conRolGerente_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/configuracion"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void actualizar_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(put("/api/v1/configuracion/dias_prestamo_default")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConfiguracionSistemaRequestDTO("20"))))
                .andExpect(status().isForbidden());
    }
}
