package com.uteq.backend.controller;

import com.uteq.backend.dto.FavoriteResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.FavoriteService;
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

@WebMvcTest(FavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "lector@correo.com", roles = "LECTOR")
class FavoriteControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

    private FavoriteResponseDTO favorite() {
        return new FavoriteResponseDTO(2L, 3L, "Clean Code", OffsetDateTime.parse("2026-01-15T10:00:00-05:00"));
    }

    @Test
    void agregar_bookExisting_devuelve201() throws Exception {
        when(favoriteService.agregar(eq(3L), any())).thenReturn(favorite());

        mockMvc.perform(post("/api/v1/favoritos/3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libroId").value(3))
                .andExpect(jsonPath("$.tituloLibro").value("Clean Code"));
    }

    @Test
    void agregar_bookInexistente_devuelve404() throws Exception {
        when(favoriteService.agregar(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Libro no encontrado: 99"));

        mockMvc.perform(post("/api/v1/favoritos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void quitar_existing_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/favoritos/3"))
                .andExpect(status().isNoContent());
    }

    @Test
    void quitar_inexistente_devuelve404() throws Exception {
        doThrow(new EntityNotFoundException("Favorito no encontrado")).when(favoriteService).quitar(eq(99L), any());

        mockMvc.perform(delete("/api/v1/favoritos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listOwns_devuelve200() throws Exception {
        when(favoriteService.listOwnsPaginated(any(), any()))
                .thenReturn(new PageImpl<>(List.of(favorite())));

        mockMvc.perform(get("/api/v1/favoritos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tituloLibro").value("Clean Code"));
    }

    @Test
    void listOwnsTodo_devuelve200() throws Exception {
        when(favoriteService.listOwns(any())).thenReturn(List.of(favorite()));

        mockMvc.perform(get("/api/v1/favoritos/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroId").value(3));
    }
}
