package com.uteq.backend.controller;

import com.uteq.backend.dto.CambioEstadoReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionHoyResponseDTO;
import com.uteq.backend.dto.ReservacionRequestDTO;
import com.uteq.backend.dto.ReservacionResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.ReservacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservacionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class ReservacionControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservacionService reservacionService;

    private final TestingAuthenticationToken biblioAuth =
            new TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");

    @Test
    void crear_datosValidos_devuelve201() throws Exception {
        ReservacionResponseDTO resp = new ReservacionResponseDTO(1L, 2L, 3L, 1, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservacionService.crear(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/reservaciones")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":2,\"libroId\":3,\"fechaRetiro\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void reservacionesDeHoy_devuelve200() throws Exception {
        ReservacionHoyResponseDTO dto = new ReservacionHoyResponseDTO(1L, "Pedro", "pedro@u.com", "Libro A", "978-0", "PENDIENTE", OffsetDateTime.now());
        when(reservacionService.buscarReservacionesDeHoy()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/hoy")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(1));
    }

    @Test
    void reservacionesProximas_devuelve200() throws Exception {
        ReservacionHoyResponseDTO dto = new ReservacionHoyResponseDTO(2L, "Maria", "maria@u.com", "Libro B", "978-1", "PENDIENTE", OffsetDateTime.now().plusDays(2));
        when(reservacionService.buscarReservacionesProximas()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/proximas")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(2));
    }

    @Test
    void cambiarEstado_listaParaRetiro_devuelve200() throws Exception {
        ReservacionResponseDTO resp = new ReservacionResponseDTO(5L, 2L, 3L, 2, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservacionService.cambiarEstado(eq(5L), any(CambioEstadoReservacionRequestDTO.class), any())).thenReturn(resp);

        mockMvc.perform(patch("/api/v1/reservaciones/5/estado")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"LISTA_PARA_RETIRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void listarPorUsuario_devuelve200() throws Exception {
        Page<ReservacionResponseDTO> page = new PageImpl<>(List.of());
        when(reservacionService.listarPorUsuario(eq(2L), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/reservaciones/usuario/2")
                        .principal(biblioAuth))
                .andExpect(status().isOk());
    }
}
