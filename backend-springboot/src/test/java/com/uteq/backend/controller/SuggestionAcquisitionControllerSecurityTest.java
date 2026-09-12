package com.uteq.backend.controller;

import com.uteq.backend.config.SecurityConfig;
import com.uteq.backend.dto.ChangeStatusSuggestionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionResponseDTO;
import com.uteq.backend.security.JwtAuthFilter;
import com.uteq.backend.security.JwtService;
import com.uteq.backend.security.UserDetailsServiceImpl;
import com.uteq.backend.service.ReportPdfService;
import com.uteq.backend.service.SuggestionAcquisitionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Módulo 9.3 del roadmap: LECTOR crea/ve las suyas, GERENTE/ADMIN listan
// todas y cambian estado. Mismo patrón que
// ConfiguracionSistemaControllerSecurityTest/LibroControllerSecurityTest --
// WebMvcTest + SecurityConfig real + MockMvc reconstruido con
// springSecurity().
@WebMvcTest(SuggestionAcquisitionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class SuggestionAcquisitionControllerSecurityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void construirMockMvcWithSeguridad() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private SuggestionAcquisitionService suggestionService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private SuggestionAcquisitionRequestDTO requestValid() {
        return new SuggestionAcquisitionRequestDTO(
                "Clean Architecture", "Robert C. Martin", null, "Complementa el material de POO");
    }

    private SuggestionAcquisitionResponseDTO responseCreated() {
        return new SuggestionAcquisitionResponseDTO(
                1L, 7L, "Clean Architecture", "Robert C. Martin", null,
                "Complementa el material de POO", "PENDIENTE", null, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withRoleReader_sePermite() throws Exception {
        when(suggestionService.create(any(), any())).thenReturn(responseCreated());

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isCreated());
    }

    // Regresion: BIBLIOTECARIO no es LECTOR ni GERENTE/ADMIN -- no debería
    // poder sugerir adquisiciones en nombre propio (ese flujo es del
    // lector) ni gestionarlas (eso es del gerente).
    @Test
    @WithMockUser(roles = "BIBLIOTECARIO")
    void create_withRoleLibrarian_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void listTodas_withRoleManager_sePermite() throws Exception {
        when(suggestionService.listAll(anyString(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion").param("estado", "PENDIENTE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void listTodas_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/sugerencias-adquisicion"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void changeStatus_withRoleManager_sePermite() throws Exception {
        when(suggestionService.changeStatus(anyLong(), anyString(), any())).thenReturn(responseCreated());

        mockMvc.perform(patch("/api/v1/sugerencias-adquisicion/1/estado")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeStatusSuggestionRequestDTO("APROBADA"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void changeStatus_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(patch("/api/v1/sugerencias-adquisicion/1/estado")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChangeStatusSuggestionRequestDTO("APROBADA"))))
                .andExpect(status().isForbidden());
    }

    // ── Gestión por demanda: mas-pedidos ──
    @Test
    @WithMockUser(roles = "GERENTE")
    void mostPedidos_withRoleManager_sePermite() throws Exception {
        when(suggestionService.getMostPedidos(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion/mas-pedidos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void mostPedidos_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(get("/api/v1/sugerencias-adquisicion/mas-pedidos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmAcquisition_withRoleAdmin_confirmaBulk() throws Exception {
        when(suggestionService.resolveIdByEmailPublic(anyString())).thenReturn(1L);
        when(suggestionService.confirmAcquisition("9781449373320", 1L)).thenReturn(3);

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion/confirmar-adquisicion")
                        .param("isbn", "9781449373320"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmadas").value(3));
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void confirmAcquisition_withRoleReader_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/sugerencias-adquisicion/confirmar-adquisicion")
                        .param("isbn", "9781449373320"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE")
    void reportPdf_withRoleManager_descargaPdf() throws Exception {
        when(suggestionService.getMostPedidosList()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion/reporte-pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    // ISBN de sugerencia: exactamente 13 dígitos numéricos, sin guiones.
    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withIsbnWithGuiones_seRechaza400() throws Exception {
        var request = new SuggestionAcquisitionRequestDTO(
                "Clean Architecture", "Robert C. Martin", "978-1449373320", "Complementa POO");

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(suggestionService, never()).create(any(), any());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withIsbnDe12Digitos_seRechaza400() throws Exception {
        var request = new SuggestionAcquisitionRequestDTO(
                "Clean Architecture", "Robert C. Martin", "978144937332", "Complementa POO");

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(suggestionService, never()).create(any(), any());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void create_withIsbnDe13Digitos_sePermite() throws Exception {
        when(suggestionService.create(any(), any())).thenReturn(responseCreated());
        var request = new SuggestionAcquisitionRequestDTO(
                "Clean Architecture", "Robert C. Martin", "9781449373320", "Complementa POO");

        mockMvc.perform(post("/api/v1/sugerencias-adquisicion")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void listOwns_withRoleReader_sePermite() throws Exception {
        org.springframework.data.domain.Page<com.uteq.backend.dto.SuggestionAcquisitionResponseDTO> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(suggestionService.listOwns(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/sugerencias-adquisicion/mias"))
                .andExpect(status().isOk());
    }
}
