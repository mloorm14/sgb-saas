package com.uteq.backend.controller;

import com.uteq.backend.dto.DevolucionCompletaResponseDTO;
import com.uteq.backend.dto.DevolucionHistorialDTO;
import com.uteq.backend.dto.DevolucionRequestDTO;
import com.uteq.backend.entity.Usuario;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.UsuarioRepository;
import com.uteq.backend.service.DevolucionService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevolucionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class DevolucionControllerTest extends WebMvcControllerTestSupport {

    org.springframework.security.authentication.TestingAuthenticationToken mockAuth = new org.springframework.security.authentication.TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevolucionService devolucionService;

    @MockitoBean
    private UsuarioRepository usuarioRepo;

    private Usuario bibliotecario() {
        Usuario u = new Usuario();
        u.setId(8L);
        u.setCorreo("biblio@correo.com");
        return u;
    }

    @Test
    void registrarDevolucion_datosValidos_devuelve201() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.of(bibliotecario()));
        when(devolucionService.registrarDevolucion(eq(1L), any(), eq(8L)))
                .thenReturn(new DevolucionCompletaResponseDTO(
                        1L, null, false, BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mockMvc.perform(post("/api/v1/devoluciones/prestamo/1")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DevolucionRequestDTO("BUENO", "ok", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prestamoId").value(1));
    }

    @Test
    void registrarDevolucion_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/devoluciones/prestamo/1")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"sin estado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarDevolucion_prestamoInexistente_devuelve404() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.of(bibliotecario()));
        when(devolucionService.registrarDevolucion(eq(99L), any(), eq(8L)))
                .thenThrow(new EntityNotFoundException("Préstamo no encontrado: 99"));

        mockMvc.perform(post("/api/v1/devoluciones/prestamo/99")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DevolucionRequestDTO("BUENO", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialDevoluciones_devuelve200() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.of(bibliotecario()));
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(devolucionService.historialDevoluciones(8L)).thenReturn(List.of(
                new DevolucionHistorialDTO(1L, "Clean Code", "9780132350884", "Ana",
                        ahora, ahora.plusDays(7), ahora.plusDays(6), "BUENO",
                        BigDecimal.ZERO, "Biblio", ahora)));

        mockMvc.perform(get("/api/v1/devoluciones/historial")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void historialDevoluciones_usuarioInexistente_devuelve404() throws Exception {
        when(usuarioRepo.findByCorreo("biblio@correo.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/devoluciones/historial")
                .principal(mockAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado: biblio@correo.com"));
    }
}
