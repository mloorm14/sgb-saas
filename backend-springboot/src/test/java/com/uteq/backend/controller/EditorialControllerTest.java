package com.uteq.backend.controller;

import com.uteq.backend.entity.Editorial;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.EditorialRepository;
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

@WebMvcTest(EditorialController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EditorialControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EditorialRepository editorialRepository;

    private Editorial editorial(Integer id, String nombre) {
        Editorial e = new Editorial();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(editorialRepository.findAll()).thenReturn(List.of(editorial(1, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void buscar_devuelve200() throws Exception {
        when(editorialRepository.findTop5ByNombreContainingIgnoreCase("Plan"))
                .thenReturn(List.of(editorial(1, "Planeta")));

        mockMvc.perform(get("/api/v1/editoriales/buscar").param("q", "Plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Planeta"));
    }

    @Test
    void crear_nuevo_devuelve201() throws Exception {
        when(editorialRepository.existsByNombreIgnoreCase("Alfaguara")).thenReturn(false);
        Editorial guardada = editorial(5, "Alfaguara");
        when(editorialRepository.save(any())).thenReturn(guardada);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Alfaguara\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void crear_duplicado_devuelve422() throws Exception {
        when(editorialRepository.existsByNombreIgnoreCase(anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/editoriales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Planeta\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
