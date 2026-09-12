package com.uteq.backend.controller;

import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.AuditService;
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

@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class AuditControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    void list_withoutFilters_devuelve200() throws Exception {
        Page page = new PageImpl(List.of());
        when(auditService.list(isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    void summary_devuelve200() throws Exception {
        when(auditService.summary()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auditoria/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void export_devuelve200() throws Exception {
        byte[] csv = "id,accion".getBytes();
        when(auditService.exportarCsv(any(), any(), any(), any())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/auditoria/export"))
                .andExpect(status().isOk());
    }
}
