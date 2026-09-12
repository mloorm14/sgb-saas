package com.uteq.backend.controller;

import com.uteq.backend.entity.Category;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.CategoryRepository;
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

@WebMvcTest(PublicCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PublicCategoryControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @Test
    void list_devuelve200WithLista() throws Exception {
        Category c = new Category();
        c.setId(1);
        c.setName("Novela");
        when(categoryRepository.findAll()).thenReturn(List.of(c));

        mockMvc.perform(get("/api/publico/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Novela"));
    }

    @Test
    void list_vacio_devuelve200() throws Exception {
        when(categoryRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/publico/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
