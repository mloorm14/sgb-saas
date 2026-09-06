package com.uteq.backend.controller;

import com.uteq.backend.dto.IdiomaRequestDTO;
import com.uteq.backend.entity.Idioma;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.IdiomaRepository;
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

@WebMvcTest(IdiomaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser
class IdiomaControllerWebMvcTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdiomaRepository idiomaRepository;

    private Idioma espanol() {
        Idioma i = new Idioma();
        i.setId(1);
        i.setNombre("Español");
        i.setCodigo("es");
        return i;
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(idiomaRepository.findAll()).thenReturn(List.of(espanol()));

        mockMvc.perform(get("/api/v1/idiomas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Español"));
    }

    @Test
    void buscar_devuelve200() throws Exception {
        when(idiomaRepository.findTop5ByNombreContainingIgnoreCase("esp")).thenReturn(List.of(espanol()));

        mockMvc.perform(get("/api/v1/idiomas/buscar").param("q", "esp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void crear_nombreNuevo_devuelve201() throws Exception {
        when(idiomaRepository.existsByNombreIgnoreCase("Quechua")).thenReturn(false);
        when(idiomaRepository.existsByCodigoIgnoreCase("que")).thenReturn(false);
        Idioma guardado = new Idioma();
        guardado.setId(4);
        guardado.setNombre("Quechua");
        guardado.setCodigo("que");
        when(idiomaRepository.save(any(Idioma.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdiomaRequestDTO("Quechua"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.nombre").value("Quechua"));
    }

    @Test
    void crear_duplicado_devuelve422() throws Exception {
        when(idiomaRepository.existsByNombreIgnoreCase("Español")).thenReturn(true);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdiomaRequestDTO("Español"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void crear_sinNombre_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_codigoOcupado_generaSufijo_devuelve201() throws Exception {
        when(idiomaRepository.existsByNombreIgnoreCase("Quechua")).thenReturn(false);
        when(idiomaRepository.existsByCodigoIgnoreCase("que")).thenReturn(true);
        when(idiomaRepository.existsByCodigoIgnoreCase("qu1")).thenReturn(false);
        Idioma guardado = new Idioma();
        guardado.setId(5);
        guardado.setNombre("Quechua");
        when(idiomaRepository.save(any(Idioma.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdiomaRequestDTO("Quechua"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void crear_nombreSinLetras_usaCodigoXx_devuelve201() throws Exception {
        when(idiomaRepository.existsByNombreIgnoreCase("123")).thenReturn(false);
        when(idiomaRepository.existsByCodigoIgnoreCase("xx")).thenReturn(false);
        Idioma guardado = new Idioma();
        guardado.setId(6);
        guardado.setNombre("123");
        when(idiomaRepository.save(any(Idioma.class))).thenReturn(guardado);

        mockMvc.perform(post("/api/v1/idiomas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdiomaRequestDTO("123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6));
    }
}
