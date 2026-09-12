package com.uteq.backend.controller;

import com.uteq.backend.dto.ChangeStatusReservationRequestDTO;
import com.uteq.backend.dto.ReservationTodayResponseDTO;
import com.uteq.backend.dto.ReservationRequestDTO;
import com.uteq.backend.dto.ReservationResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.ReservationService;
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

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class ReservationControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private final TestingAuthenticationToken biblioAuth =
            new TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");

    @Test
    void create_dataValids_devuelve201() throws Exception {
        ReservationResponseDTO resp = new ReservationResponseDTO(1L, 2L, 3L, 1, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservationService.create(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/reservaciones")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":2,\"libroId\":3,\"fechaRetiro\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void reservationsToday_devuelve200() throws Exception {
        ReservationTodayResponseDTO dto = new ReservationTodayResponseDTO(1L, "Pedro", "pedro@u.com", "Libro A", "978-0", "PENDIENTE", OffsetDateTime.now());
        when(reservationService.searchReservationsToday()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/hoy")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(1));
    }

    @Test
    void reservationsNexts_devuelve200() throws Exception {
        ReservationTodayResponseDTO dto = new ReservationTodayResponseDTO(2L, "Maria", "maria@u.com", "Libro B", "978-1", "PENDIENTE", OffsetDateTime.now().plusDays(2));
        when(reservationService.searchReservationsNexts()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/reservaciones/proximas")
                        .principal(biblioAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservacionId").value(2));
    }

    @Test
    void changeStatus_listaForPickup_devuelve200() throws Exception {
        ReservationResponseDTO resp = new ReservationResponseDTO(5L, 2L, 3L, 2, OffsetDateTime.now(), OffsetDateTime.now().plusDays(3));
        when(reservationService.changeStatus(eq(5L), any(ChangeStatusReservationRequestDTO.class), any())).thenReturn(resp);

        mockMvc.perform(patch("/api/v1/reservaciones/5/estado")
                        .principal(biblioAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevoEstado\":\"LISTA_PARA_RETIRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void listByUser_devuelve200() throws Exception {
        Page<ReservationResponseDTO> page = new PageImpl<>(List.of());
        when(reservationService.listByUser(eq(2L), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/reservaciones/usuario/2")
                        .principal(biblioAuth))
                .andExpect(status().isOk());
    }
}
