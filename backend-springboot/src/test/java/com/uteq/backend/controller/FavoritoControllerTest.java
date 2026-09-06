package com.uteq.backend.controller;

import com.uteq.backend.dto.FavoritoResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.FavoritoService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoritoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "lector@correo.com", roles = "LECTOR")
class FavoritoControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoritoService favoritoService;

    private FavoritoResponseDTO favorito() {
        return new FavoritoResponseDTO(2L, 3L, "Clean Code", OffsetDateTime.parse("2026-01-15T10:00:00-05:00"));
    }

    @Test
    void agregar_libroExistente_devuelve201() throws Exception {
        when(favoritoService.agregar(eq(3L), any())).thenReturn(favorito());

        mockMvc.perform(post("/api/v1/favoritos/3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libroId").value(3))
                .andExpect(jsonPath("$.tituloLibro").value("Clean Code"));
    }

    @Test
    void agregar_libroInexistente_devuelve404() throws Exception {
        when(favoritoService.agregar(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Libro no encontrado: 99"));

        mockMvc.perform(post("/api/v1/favoritos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quitar_existente_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/favoritos/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    void quitar_inexistente_devuelve404() throws Exception {
        doThrow(new EntityNotFoundException("Favorito no encontrado")).when(favoritoService).quitar(eq(99L), any());

        mockMvc.perform(delete("/api/v1/favoritos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listarPropios_devuelve200() throws Exception {
        when(favoritoService.listarPropiosPaginado(any(), any()))
                .thenReturn(new PageImpl<>(List.of(favorito())));

        mockMvc.perform(get("/api/v1/favoritos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tituloLibro").value("Clean Code"));
    }

    @Test
    void listarPropiosTodo_devuelve200() throws Exception {
        when(favoritoService.listarPropios(any())).thenReturn(List.of(favorito()));

        mockMvc.perform(get("/api/v1/favoritos/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroId").value(3));
    }
}
