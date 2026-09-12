package com.uteq.backend.controller;

import com.uteq.backend.entity.Publisher;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.PublisherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublisherController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PublisherControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublisherRepository publisherRepository;

    private Publisher publisher(Integer id, String name) {
        Publisher e = new Publisher();
        e.setId(id);
        e.setName(name);
        return e;
    }

    @Test
    void list_devuelve200() throws Exception {
        when(publisherRepository.findAll()).thenReturn(List.of(publisher(1, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void search_devuelve200() throws Exception {
        when(publisherRepository.findTop5ByNameContainingIgnoreCase("Plan"))
                .thenReturn(List.of(publisher(1, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales/buscar").param("q", "Plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void create_fresh_devuelve201() throws Exception {
        when(publisherRepository.existsByNameIgnoreCase("Alfaguara")).thenReturn(false);
        Publisher guardada = publisher(5, "Alfaguara");
        when(publisherRepository.save(any())).thenReturn(guardada);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Alfaguara\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void create_duplicate_devuelve422() throws Exception {
        when(publisherRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Planeta\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
