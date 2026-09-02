package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.ReservacionService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservacionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class ReservacionControllerSecurityTest {

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
    private ReservacionService reservacionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void reservacionesDeHoy_conRolBibliotecario_sePermite() throws Exception {
        when(reservacionService.buscarReservacionesDeHoy()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservaciones/hoy"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void reservacionesDeHoy_conRolGerente_sePermite() throws Exception {
        when(reservacionService.buscarReservacionesDeHoy()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservaciones/hoy"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void reservacionesDeHoy_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/reservaciones/hoy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reservacionesDeHoy_conRolAdmin_sePermite() throws Exception {
        when(reservacionService.buscarReservacionesDeHoy()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservaciones/hoy"))
                .andExpect(status().isOk());
    }
}
