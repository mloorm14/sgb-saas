package com.uteq.backend.controller;

import com.uteq.backend.entity.Categoria;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicoCategoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PublicoCategoriaControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoriaRepository categoriaRepository;

    @Test
    void listar_devuelve200ConLista() throws Exception {
        Categoria c = new Categoria();
        c.setId(1);
        c.setNombre("Novela");
        when(categoriaRepository.findAll()).thenReturn(List.of(c));

        mockMvc.perform(get("/api/publico/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Novela"));
    }

    @Test
    void listar_vacio_devuelve200() throws Exception {
        when(categoriaRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/publico/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
