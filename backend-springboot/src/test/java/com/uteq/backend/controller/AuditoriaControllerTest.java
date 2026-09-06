package com.uteq.backend.controller;

import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class AuditoriaControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditoriaService auditoriaService;

    @Test
    void listar_sinFiltros_devuelve200() throws Exception {
        Page page = new PageImpl(List.of());
        when(auditoriaService.listar(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    void resumen_devuelve200() throws Exception {
        when(auditoriaService.resumen()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auditoria/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void export_devuelve200() throws Exception {
        byte[] csv = "id,accion".getBytes();
        when(auditoriaService.exportarCsv(any(), any(), any(), any())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/auditoria/export"))
                .andExpect(status().isOk());
    }
}
