package com.uteq.backend.controller;

import com.uteq.backend.entity.CategoriaDano;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.CategoriaDanoRepository;
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

@WebMvcTest(CategoriaDanoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class CategoriaDanoControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaDanoRepository repo;

    private CategoriaDano categoria() {
        CategoriaDano c = new CategoriaDano();
        c.setId(2);
        c.setNombre("Portada");
        c.setActivo(true);
        return c;
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(repo.findAll()).thenReturn(List.of(categoria()));

        mockMvc.perform(get("/api/v1/categorias-dano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Portada"));
    }

    @Test
    void crear_nombreValido_devuelve201() throws Exception {
        when(repo.findByNombre("Portada")).thenReturn(Optional.empty());
        when(repo.save(any(CategoriaDano.class))).thenReturn(categoria());

        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoriaDanoController.CategoriaRequest("Portada"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void crear_nombreVacio_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_duplicado_devuelve422() throws Exception {
        when(repo.findByNombre("Portada")).thenReturn(Optional.of(categoria()));

        mockMvc.perform(post("/api/v1/categorias-dano")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void actualizar_existente_devuelve200() throws Exception {
        when(repo.findById(2)).thenReturn(Optional.of(categoria()));
        when(repo.save(any(CategoriaDano.class))).thenReturn(categoria());

        mockMvc.perform(put("/api/v1/categorias-dano/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Portada"));
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        when(repo.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/categorias-dano/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Portada\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_existente_devuelve204() throws Exception {
        when(repo.findById(2)).thenReturn(Optional.of(categoria()));
        when(repo.save(any(CategoriaDano.class))).thenReturn(categoria());

        mockMvc.perform(delete("/api/v1/categorias-dano/2"))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        when(repo.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/categorias-dano/99"))
                .andExpect(status().isNotFound());
    }
}
