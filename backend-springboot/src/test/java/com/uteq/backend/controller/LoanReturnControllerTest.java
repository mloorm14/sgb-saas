package com.uteq.backend.controller;

import com.uteq.backend.dto.LoanReturnFullResponseDTO;
import com.uteq.backend.dto.LoanReturnHistoryDTO;
import com.uteq.backend.dto.LoanReturnRequestDTO;
import com.uteq.backend.entity.User;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.repository.UserRepository;
import com.uteq.backend.service.LoanReturnService;
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

@WebMvcTest(LoanReturnController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "biblio@correo.com", roles = "BIBLIOTECARIO")
class LoanReturnControllerTest extends WebMvcControllerTestSupport {

    org.springframework.security.authentication.TestingAuthenticationToken mockAuth = new org.springframework.security.authentication.TestingAuthenticationToken("biblio@correo.com", null, "ROLE_BIBLIOTECARIO");


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanReturnService loanReturnService;

    @MockitoBean
    private UserRepository userRepo;

    private User librarian() {
        User u = new User();
        u.setId(8L);
        u.setEmail("biblio@correo.com");
        return u;
    }

    @Test
    void registerLoanReturn_dataValids_devuelve201() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.of(librarian()));
        when(loanReturnService.registerLoanReturn(eq(1L), any(), eq(8L)))
                .thenReturn(new LoanReturnFullResponseDTO(
                        1L, null, false, BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        mockMvc.perform(post("/api/v1/devoluciones/prestamo/1")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoanReturnRequestDTO("BUENO", "ok", List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prestamoId").value(1));
    }

    @Test
    void registerLoanReturn_withoutStatus_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/devoluciones/prestamo/1")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"sin estado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerLoanReturn_loanInexistente_devuelve404() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.of(librarian()));
        when(loanReturnService.registerLoanReturn(eq(99L), any(), eq(8L)))
                .thenThrow(new EntityNotFoundException("Préstamo no encontrado: 99"));

        mockMvc.perform(post("/api/v1/devoluciones/prestamo/99")
                .principal(mockAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoanReturnRequestDTO("BUENO", null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyLoanReturns_devuelve200() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.of(librarian()));
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(loanReturnService.historyLoanReturns(8L)).thenReturn(List.of(
                new LoanReturnHistoryDTO(1L, "Clean Code", "9780132350884", "Ana",
                        ahora, ahora.plusDays(7), ahora.plusDays(6), "BUENO",
                        BigDecimal.ZERO, "Biblio", ahora)));

        mockMvc.perform(get("/api/v1/devoluciones/historial")
                .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void historyLoanReturns_userInexistente_devuelve404() throws Exception {
        when(userRepo.findByEmail("biblio@correo.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/devoluciones/historial")
                .principal(mockAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Usuario no encontrado: biblio@correo.com"));
    }
}
