package com.uteq.backend.controller;

import com.uteq.backend.dto.TypeDamageDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.TypeDamageService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TypeDamageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class TypeDamageControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TypeDamageService typeDamageService;

    private TypeDamageDTO dto() {
        return new TypeDamageDTO(3, "Rasgado", 2, "Portada", "FIJO", new BigDecimal("5.00"));
    }

    private TypeDamageController.TypeDamageRequestDTO requestValid() {
        return new TypeDamageController.TypeDamageRequestDTO("Rasgado", 2, "FIJO", new BigDecimal("5.00"));
    }

    @Test
    void list_devuelve200() throws Exception {
        when(typeDamageService.listAll()).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/v1/tipos-dano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Rasgado"));
    }

    @Test
    void create_dataValids_devuelve201() throws Exception {
        when(typeDamageService.create("Rasgado", 2, "FIJO", new BigDecimal("5.00"))).thenReturn(dto());

        mockMvc.perform(post("/api/v1/tipos-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void create_withoutName_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/tipos-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoriaId":2,"tipoCosto":"FIJO","valor":5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_existing_devuelve200() throws Exception {
        when(typeDamageService.update(eq(3), eq("Rasgado"), eq(2), eq("FIJO"), any()))
                .thenReturn(dto());

        mockMvc.perform(put("/api/v1/tipos-dano/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaNombre").value("Portada"));
    }

    @Test
    void update_inexistente_devuelve404() throws Exception {
        when(typeDamageService.update(eq(99), any(), any(), any(), any()))
                .thenThrow(new EntityNotFoundException("Tipo de daño no encontrado: 99"));

        mockMvc.perform(put("/api/v1/tipos-dano/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValid())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existing_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/tipos-dano/3"))
                .andExpect(status().isNoContent());
        verify(typeDamageService).delete(3);
    }

    @Test
    void delete_inexistente_devuelve404() throws Exception {
        doThrow(new EntityNotFoundException("Tipo de daño no encontrado: 99"))
                .when(typeDamageService).delete(99);

        mockMvc.perform(delete("/api/v1/tipos-dano/99"))
                .andExpect(status().isNotFound());
    }
}
