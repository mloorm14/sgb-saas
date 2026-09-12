package com.uteq.backend.controller;

import com.uteq.backend.dto.HistoryLoanDTO;
import com.uteq.backend.dto.ReservationActiveDTO;
import com.uteq.backend.dto.UserLoansManagementDTO;
import com.uteq.backend.dto.UserSuggestionDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.LoansManagementService;
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

@WebMvcTest(LoansManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class LoansManagementControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoansManagementService loansManagementService;

    @Test
    void searchUser_existing_devuelve200() throws Exception {
        when(loansManagementService.searchByEmail("ana@correo.com"))
                .thenReturn(new UserLoansManagementDTO(
                        2L, "Ana Pérez", "0102030405", "ana@correo.com",
                        List.of("LECTOR"), "ACTIVO", BigDecimal.ZERO, 0L, 7));

        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "ana@correo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez"));
    }

    @Test
    void searchUser_emailInvalid_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "no-es-correo"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchUser_inexistente_devuelve404() throws Exception {
        when(loansManagementService.searchByEmail("nadie@correo.com"))
                .thenThrow(new EntityNotFoundException("Usuario no encontrado"));

        mockMvc.perform(get("/api/v1/prestamos/gestion/buscar-usuario").param("correo", "nadie@correo.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void suggestionsUsers_devuelve200() throws Exception {
        when(loansManagementService.suggestionsUsers("ana"))
                .thenReturn(List.of(new UserSuggestionDTO(2L, "Ana Pérez", "ana@correo.com", "ACTIVO")));

        mockMvc.perform(get("/api/v1/prestamos/gestion/sugerencias-usuarios").param("correo", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").value("ana@correo.com"));
    }

    @Test
    void reservationActive_existing_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(loansManagementService.reservationActive(2L)).thenReturn(new ReservationActiveDTO(
                9L, 3L, "Clean Code", List.of("Uncle Bob"), "9780132350884",
                ahora, ahora.plusDays(2), 7, (short) 2008, (short) 1, (short) 3, "A1",
                List.of("Técnica"), true));

        mockMvc.perform(get("/api/v1/prestamos/gestion/reserva-activa").param("usuarioId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Clean Code"));
    }

    @Test
    void reservationActive_withoutReservation_devuelve404() throws Exception {
        when(loansManagementService.reservationActive(2L))
                .thenThrow(new EntityNotFoundException("Sin reserva vigente"));

        mockMvc.perform(get("/api/v1/prestamos/gestion/reserva-activa").param("usuarioId", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(loansManagementService.history(2L)).thenReturn(List.of(new HistoryLoanDTO(
                1L, 3L, "Clean Code", "9780132350884", List.of("Uncle Bob"), List.of("Técnica"),
                ahora, ahora.plusDays(7), null, "ACTIVO", false, BigDecimal.ZERO, "Ana", "ana@correo.com")));

        mockMvc.perform(get("/api/v1/prestamos/gestion/historial").param("usuarioId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoNombre").value("ACTIVO"));
    }
}
