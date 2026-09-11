import os

base = 'backend-springboot/src/test/java/com/uteq/backend/controller/'

content = """package com.uteq.backend.controller;

import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.LibroIsbnLookupService;
import com.uteq.backend.service.LibroService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibroController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class LibroControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibroService libroService;

    @MockitoBean
    private LibroIsbnLookupService libroIsbnLookupService;

    @Test
    @WithMockUser(roles = "LECTOR")
    void listar_devuelve200() throws Exception {
        Page<LibroResponseDTO> page = new PageImpl<>(List.of());
        when(libroService.listarConFiltros(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/libros"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void buscar_devuelve200() throws Exception {
        when(libroService.buscarPorId(1L)).thenReturn(new LibroResponseDTO());

        mockMvc.perform(get("/api/v1/libros/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void sugerencias_devuelve200() throws Exception {
        when(libroService.sugerir("java")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/libros/sugerencias").param("texto", "java"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendientes_devuelve200() throws Exception {
        Page<LibroResponseDTO> page = new PageImpl<>(List.of());
        when(libroService.listarPendientes(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/libros/pendientes"))
                .andExpect(status().isOk());
    }
}
"""

with open(base + 'LibroControllerTest.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print("Written LibroControllerTest.java")
