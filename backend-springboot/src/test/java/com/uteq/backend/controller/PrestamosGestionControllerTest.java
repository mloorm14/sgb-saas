package com.uteq.backend.controller;

import com.uteq.backend.dto.HistorialPrestamoDTO;
import com.uteq.backend.dto.ReservaActivaDTO;
import com.uteq.backend.dto.UsuarioPrestamosGestionDTO;
import com.uteq.backend.dto.UsuarioSugerenciaDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.PrestamosGestionService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrestamosGestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class PrestamosGestionControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestamosGestionService prestamosGestionService;

    @Test
    void buscarUsuario_existente_devuelve200() throws Exception {
        when(prestamosGestionService.buscarPorCorreo("ana@correo.com"))
                .thenReturn(new UsuarioPrestamosGestionDTO(
                        2L, "Ana Pérez", "0102030405", "ana@correo.com",
                        List.of("LECTOR"), "ACTIVO", BigDecimal.ZERO, 0L, 7));

        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez"));
    }

    @Test
    void buscarUsuario_correoInvalido_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "no-es-correo"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscarUsuario_inexistente_devuelve404() throws Exception {
        when(prestamosGestionService.buscarPorCorreo("nadie@correo.com"))
                .thenThrow(new EntityNotFoundException("Usuario no encontrado"));

        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "nadie@correo.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sugerenciasUsuarios_devuelve200() throws Exception {
        when(prestamosGestionService.sugerenciasUsuarios("ana"))
                .thenReturn(List.of(new UsuarioSugerenciaDTO(2L, "Ana Pérez", "ana@correo.com", "ACTIVO")));

        mockMvc.perform(get("/api/v1/prestamos/gestion/sugerencias-usuarios").param("correo", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").value("ana@correo.com"));
    }

    @Test
    void reservaActiva_existente_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(prestamosGestionService.reservaActiva(2L)).thenReturn(new ReservaActivaDTO(
                9L, 3L, "Clean Code", List.of("Uncle Bob"), "9780132350884",
                ahora, ahora.plusDays(2), 7, (short) 2008, (short) 1, (short) 3, "A1",
                List.of("Técnica"), true));

        mockMvc.perform(get("/api/v1/prestamos/gestion/reserva-activa").param("usuarioId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Clean Code"));
    }

    @Test
    void reservaActiva_sinReserva_devuelve404() throws Exception {
        when(prestamosGestionService.reservaActiva(2L))
                .thenThrow(new EntityNotFoundException("Sin reserva vigente"));

        mockMvc.perform(get("/api/v1/prestamos/gestion/reserva-activa").param("usuarioId", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void historial_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(prestamosGestionService.historial(2L)).thenReturn(List.of(new HistorialPrestamoDTO(
                1L, 3L, "Clean Code", "9780132350884", List.of("Uncle Bob"), List.of("Técnica"),
                ahora, ahora.plusDays(7), null, "ACTIVO", false, BigDecimal.ZERO, "Ana", "ana@correo.com")));

        mockMvc.perform(get("/api/v1/prestamos/gestion/historial").param("usuarioId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoNombre").value("ACTIVO"));
    }
}
