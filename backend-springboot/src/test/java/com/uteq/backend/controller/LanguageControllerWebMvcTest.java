package com.uteq.backend.controller;

import com.uteq.backend.dto.LanguageRequestDTO;
import com.uteq.backend.entity.Language;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.LanguageRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LanguageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser
class LanguageControllerWebMvcTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LanguageRepository languageRepository;

    private Language espanol() {
        Language i = new Language();
        i.setId(1);
        i.setName("Español");
        i.setCode("es");
        return i;
    }

    @Test
    void list_devuelve200() throws Exception {
        when(languageRepository.findAll()).thenReturn(List.of(espanol()));

        mockMvc.perform(get("/api/v1/idiomas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Español"));
    }

    @Test
    void search_devuelve200() throws Exception {
        when(languageRepository.findTop5ByNameContainingIgnoreCase("esp")).thenReturn(List.of(espanol()));

        mockMvc.perform(get("/api/v1/idiomas/buscar").param("q", "esp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void create_nameFresh_devuelve201() throws Exception {
        when(languageRepository.existsByNameIgnoreCase("Quechua")).thenReturn(false);
        when(languageRepository.existsByCodeIgnoreCase("que")).thenReturn(false);
        Language guardado = new Language();
        guardado.setId(4);
        guardado.setName("Quechua");
        guardado.setCode("que");
        when(languageRepository.save(any(Language.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LanguageRequestDTO("Quechua"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.nombre").value("Quechua"));
    }

    @Test
    void create_duplicate_devuelve422() throws Exception {
        when(languageRepository.existsByNameIgnoreCase("Español")).thenReturn(true);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LanguageRequestDTO("Español"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_withoutName_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_codeOcupado_generaSufijo_devuelve201() throws Exception {
        when(languageRepository.existsByNameIgnoreCase("Quechua")).thenReturn(false);
        when(languageRepository.existsByCodeIgnoreCase("que")).thenReturn(true);
        when(languageRepository.existsByCodeIgnoreCase("qu1")).thenReturn(false);
        Language guardado = new Language();
        guardado.setId(5);
        guardado.setName("Quechua");
        when(languageRepository.save(any(Language.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LanguageRequestDTO("Quechua"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void create_nameWithoutLetras_usaCodeXx_devuelve201() throws Exception {
        when(languageRepository.existsByNameIgnoreCase("123")).thenReturn(false);
        when(languageRepository.existsByCodeIgnoreCase("xx")).thenReturn(false);
        Language guardado = new Language();
        guardado.setId(6);
        guardado.setName("123");
        when(languageRepository.save(any(Language.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LanguageRequestDTO("123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }
}
