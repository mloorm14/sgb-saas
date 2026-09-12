package com.uteq.backend.controller;

import com.uteq.backend.entity.CategoryDamage;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.CategoryDamageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryDamageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class CategoryDamageControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryDamageRepository repo;

    private CategoryDamage category() {
        CategoryDamage c = new CategoryDamage();
        c.setId(2);
        c.setName("Portada");
        c.setActive(true);
        return c;
    }

    @Test
    void list_devuelve200() throws Exception {
        when(repo.findAll()).thenReturn(List.of(category()));

        mockMvc.perform(get("/api/v1/categorias-dano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Portada"));
    }

    @Test
    void create_nameValid_devuelve201() throws Exception {
        when(repo.findByName("Portada")).thenReturn(Optional.empty());
        when(repo.save(any(CategoryDamage.class))).thenReturn(category());

        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoryDamageController.CategoryRequest("Portada"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void create_nameVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nameNulo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicate_devuelve422() throws Exception {
        when(repo.findByName("Portada")).thenReturn(Optional.of(category()));

        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_existing_devuelve200() throws Exception {
        when(repo.findById(2)).thenReturn(Optional.of(category()));
        when(repo.save(any(CategoryDamage.class))).thenReturn(category());

        mockMvc.perform(put("/api/v1/categorias-dano/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Portada"));
    }

    @Test
    void update_inexistente_devuelve404() throws Exception {
        when(repo.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/categorias-dano/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existing_devuelve204() throws Exception {
        when(repo.findById(2)).thenReturn(Optional.of(category()));
        when(repo.save(any(CategoryDamage.class))).thenReturn(category());

        mockMvc.perform(delete("/api/v1/categorias-dano/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_inexistente_devuelve404() throws Exception {
        when(repo.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/categorias-dano/99"))
                .andExpect(status().isNotFound());
    }
}
