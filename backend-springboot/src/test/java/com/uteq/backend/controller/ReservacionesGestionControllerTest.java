package com.uteq.backend.controller;

import com.uteq.backend.dto.UsuarioReservacionesGestionDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.ReservacionesGestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservacionesGestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class ReservacionesGestionControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservacionesGestionService reservacionesGestionService;

    @Test
    void buscarUsuario_correoValido_devuelve200() throws Exception {
        UsuarioReservacionesGestionDTO dto = new UsuarioReservacionesGestionDTO(5L, "Maria", "maria@uteq.edu.ec", "LECTOR", 2L, 3);
        when(reservacionesGestionService.buscarPorCorreo("maria@uteq.edu.ec")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/reservaciones/gestion/buscar-usuario")
                        .param("correo", "maria@uteq.edu.ec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void historialReservaciones_devuelve200() throws Exception {
        when(reservacionesGestionService.historialReservaciones(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservaciones/gestion/historial-reservaciones")
                        .param("usuarioId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
