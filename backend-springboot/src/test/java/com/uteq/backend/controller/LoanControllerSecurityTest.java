package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.LoanReturnResponseDTO;
import com.uteq.backend.dto.LoanResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.LoanService;
import com.uteq.backend.service.ReportPdfService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class LoanControllerSecurityTest {

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
    private LoanService loanService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    // ── GERENTE: 200 en todos los reportes ──

    @Test
    @WithMockUser(roles = "GERENTE")
    void booksMostLoaned_withRoleManager_sePermite() throws Exception {
        when(loanService.reportBooksMostLoaned(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void delinquency_withRoleManager_sePermite() throws Exception {
        when(loanService.reportDelinquency(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void usage_withRoleManager_sePermite() throws Exception {
        when(loanService.reportUsageByPeriod(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void inventory_withRoleManager_sePermite() throws Exception {
        when(loanService.reportInventory(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void overdues_withRoleManager_sePermite() throws Exception {
        when(loanService.reportLoansOverdues(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void categoriesDemanded_withRoleManager_sePermite() throws Exception {
        when(loanService.reportCategoriesDemanded(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas"))
                .andExpect(status().isOk());
    }

    // ── ADMIN: 200 en todos los reportes ──

    @Test
    @WithMockUser(roles = "ADMIN")
    void booksMostLoaned_withRoleAdmin_sePermite() throws Exception {
        when(loanService.reportBooksMostLoaned(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delinquency_withRoleAdmin_sePermite() throws Exception {
        when(loanService.reportDelinquency(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk());
    }

    // ── BIBLIOTECARIO: 403 en todos los reportes ──

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void booksMostLoaned_withRoleLibrarian_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void delinquency_withRoleLibrarian_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isForbidden());
    }

    // ── LECTOR: 403 en todos los reportes ──

    @Test
    @WithMockUser(roles = "LECTOR")
    void booksMostLoaned_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void delinquency_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isForbidden());
    }

    // ── crear() / registrarDevolucion(): BIBLIOTECARIO debe poder ──
    // (corrección del hueco de rol reportado en la auditoría del Dr.
    // Guerrero — antes de este fix, BIBLIOTECARIO recibía 403 aquí).

    private static final String CUERPO_PRESTAMO_VALIDO =
            "{\"usuarioId\":2,\"libroId\":3,\"diasPrestamo\":7}";

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void create_withRoleLibrarian_sePermite() throws Exception {
        when(loanService.create(any(), any())).thenReturn(
                new LoanResponseDTO(1L, 2L, 3L, 4L, null,
                        OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), null, (short) 0, 1));

        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_PRESTAMO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void registerLoanReturn_withRoleLibrarian_sePermite() throws Exception {
        when(loanService.registerLoanReturn(1L))
                .thenReturn(new LoanReturnResponseDTO(1L, false, BigDecimal.ZERO));

        mockMvc.perform(post("/api/v1/prestamos/1/devolucion"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_PRESTAMO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void registerLoanReturn_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/prestamos/1/devolucion"))
                .andExpect(status().isForbidden());
    }
}
