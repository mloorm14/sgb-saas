package com.uteq.backend.controller;

import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.BookIsbnLookupService;
import com.uteq.backend.service.BookService;
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

@WebMvcTest(BookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BookControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private BookIsbnLookupService bookIsbnLookupService;

    @Test
    @WithMockUser(roles = "LECTOR")
    void list_devuelve200() throws Exception {
        Page<BookResponseDTO> page = new PageImpl<>(List.of());
        when(bookService.listWithFilters(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/libros"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void search_devuelve200() throws Exception {
        when(bookService.searchById(1L)).thenReturn(org.mockito.Mockito.mock(BookResponseDTO.class));

        mockMvc.perform(get("/api/v1/libros/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "LECTOR")
    void suggestions_devuelve200() throws Exception {
        when(bookService.sugerir("java")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/libros/sugerencias").param("texto", "java"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pendientes_devuelve200() throws Exception {
        Page<BookResponseDTO> page = new PageImpl<>(List.of());
        when(bookService.listPending(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/libros/pendientes"))
                .andExpect(status().isOk());
    }
}
