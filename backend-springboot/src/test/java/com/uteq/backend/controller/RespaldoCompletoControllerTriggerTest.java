package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.RespaldoCompletoEjecutor;
import com.uteq.backend.service.RespaldoCompletoService;
import com.uteq.backend.service.RespaldoEnCursoException;
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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El trigger ya no hace proxy a Node.js: ejecuta en background dentro del
// backend y el 429 solo sale del cerrojo real del ejecutor.
@WebMvcTest(RespaldoCompletoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class RespaldoCompletoControllerTriggerTest {

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
    private RespaldoCompletoService respaldoCompletoService;

    @MockitoBean
    private RespaldoCompletoEjecutor respaldoCompletoEjecutor;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "ADMIN")
    void trigger_conRolAdmin_responde202() throws Exception {
        doNothing().when(respaldoCompletoEjecutor).dispararManual();

        mockMvc.perform(post("/api/v1/admin/respaldo-completo/trigger"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value("Backup completo iniciado"));

        verify(respaldoCompletoEjecutor).dispararManual();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void trigger_conRespaldoEnCurso_responde429ConMensaje() throws Exception {
        doThrow(new RespaldoEnCursoException("Ya hay un respaldo en ejecucion"))
                .when(respaldoCompletoEjecutor).dispararManual();

        mockMvc.perform(post("/api/v1/admin/respaldo-completo/trigger"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value("Ya hay un respaldo en ejecucion"));
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void trigger_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/admin/respaldo-completo/trigger"))
                .andExpect(status().isForbidden());
    }
}
