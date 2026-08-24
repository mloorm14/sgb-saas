package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.PrestamoService;
import com.uteq.backend.service.ReportePdfService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrestamoController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class PrestamoControllerSecurityTest {

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
    private PrestamoService prestamoService;

    @MockitoBean
    private ReportePdfService reportePdfService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    // ── GERENTE: 200 en todos los reportes ──

    @Test
    @WithMockUser(roles = "GERENTE")
    void librosMasPrestados_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reporteLibrosMasPrestados(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void morosidad_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reporteMorosidad(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void uso_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reporteUsoPorPeriodo(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void inventario_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reporteInventario(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void vencidos_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reportePrestamosVencidos(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void categoriasDemandadas_conRolGerente_sePermite() throws Exception {
        when(prestamoService.reporteCategoriasDemandadas(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas"))
                .andExpect(status().isOk());
    }

    // ── ADMIN: 200 en todos los reportes ──

    @Test
    @WithMockUser(roles = "ADMIN")
    void librosMasPrestados_conRolAdmin_sePermite() throws Exception {
        when(prestamoService.reporteLibrosMasPrestados(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void morosidad_conRolAdmin_sePermite() throws Exception {
        when(prestamoService.reporteMorosidad(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk());
    }

    // ── BIBLIOTECARIO: 403 en todos los reportes ──

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void librosMasPrestados_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void morosidad_conRolBibliotecario_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isForbidden());
    }

    // ── LECTOR: 403 en todos los reportes ──

    @Test
    @WithMockUser(roles = "LECTOR")
    void librosMasPrestados_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void morosidad_conRolLector_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isForbidden());
    }
}
