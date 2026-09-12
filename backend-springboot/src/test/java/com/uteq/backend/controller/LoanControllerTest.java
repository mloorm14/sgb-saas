package com.uteq.backend.controller;

import com.uteq.backend.dto.LoanReturnResponseDTO;
import com.uteq.backend.dto.BookMostLoanedDetailedResponseDTO;
import com.uteq.backend.dto.BookMostLoanedResponseDTO;
import com.uteq.backend.dto.LoanActiveResponseDTO;
import com.uteq.backend.dto.LoanRequestDTO;
import com.uteq.backend.dto.LoanResponseDTO;
import com.uteq.backend.dto.RenewalResponseDTO;
import com.uteq.backend.dto.ReportCategoriesDemandedResponseDTO;
import com.uteq.backend.dto.ReportInventoryResponseDTO;
import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.dto.ReportOverduesResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.LoanService;
import com.uteq.backend.service.ReportPdfService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class LoanControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanService loanService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    private LoanResponseDTO loan() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        return new LoanResponseDTO(1L, 2L, 3L, 4L, null, ahora, ahora.plusDays(7), null, (short) 0, 1);
    }

    @Test
    void create_dataValids_devuelve201() throws Exception {
        when(loanService.create(any(), any())).thenReturn(loan());

        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoanRequestDTO(2L, null, 3L, 7, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.libroId").value(3));
    }

    @Test
    void create_withoutBook_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":2,\"diasPrestamo\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerLoanReturn_existing_devuelve200() throws Exception {
        when(loanService.registerLoanReturn(1L))
                .thenReturn(new LoanReturnResponseDTO(1L, false, BigDecimal.ZERO));

        mockMvc.perform(post("/api/v1/prestamos/1/devolucion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestamoId").value(1));
    }

    @Test
    void registerLoanReturn_inexistente_devuelve404() throws Exception {
        when(loanService.registerLoanReturn(99L))
                .thenThrow(new EntityNotFoundException("Préstamo no encontrado: 99"));

        mockMvc.perform(post("/api/v1/prestamos/99/devolucion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Préstamo no encontrado: 99"));
    }

    @Test
    void renew_existing_devuelve200() throws Exception {
        OffsetDateTime fresh = OffsetDateTime.parse("2026-01-22T10:00:00-05:00");
        when(loanService.renew(eq(1L), any()))
                .thenReturn(new RenewalResponseDTO(1L, fresh, (short) 1, (short) 1));

        mockMvc.perform(post("/api/v1/prestamos/1/renovacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestamoId").value(1))
                .andExpect(jsonPath("$.renovacionesRealizadas").value(1));
    }

    @Test
    void listByUser_devuelve200() throws Exception {
        when(loanService.listByUser(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(loan())));

        mockMvc.perform(get("/api/v1/prestamos/usuario/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(2));
    }

    @Test
    void listActivesByUser_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(loanService.listActivesByUser(eq(2L), any()))
                .thenReturn(List.of(new LoanActiveResponseDTO(
                        1L, "Clean Code", "9780132350884", ahora, ahora.plusDays(3), 3, "ACTIVO")));

        mockMvc.perform(get("/api/v1/prestamos/usuario/2/activos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void reportBooksMostLoaned_devuelve200() throws Exception {
        when(loanService.reportBooksMostLoaned(any(), any(), any()))
                .thenReturn(List.of(new BookMostLoanedResponseDTO(1L, "Clean Code", "9780132350884", 12L)));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPrestamos").value(12));
    }

    @Test
    void reportBooksMostLoanedDetailed_devuelve200() throws Exception {
        when(loanService.reportBooksMostLoanedDetailedPaginated(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(detailed())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados-detallado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Clean Code"));
    }

    @Test
    void reportBooksMostLoanedDetailedTodo_devuelve200() throws Exception {
        when(loanService.reportBooksMostLoanedDetailed(any(), any(), any(), any()))
                .thenReturn(List.of(detailed()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados-detallado/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isbn").value("9780132350884"));
    }

    @Test
    void reportDelinquency_devuelve200() throws Exception {
        when(loanService.reportDelinquencyPaginated(any(), any()))
                .thenReturn(new PageImpl<>(List.of(delinquency())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].correo").value("lector@correo.com"));
    }

    @Test
    void reportDelinquencyTodo_devuelve200() throws Exception {
        when(loanService.reportDelinquency(any())).thenReturn(List.of(delinquency()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value(2));
    }

    @Test
    void reportUsageByPeriod_devuelve200() throws Exception {
        OffsetDateTime period = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(loanService.reportUsageByPeriodPaginated(eq("dia"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new ReportUsageByPeriodResponseDTO(period, 4L, 2L))));

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso").param("granularidad", "dia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalPrestamos").value(4));
    }

    @Test
    void reportUsageByPeriodTodo_devuelve200() throws Exception {
        OffsetDateTime period = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(loanService.reportUsageByPeriod(eq("semana"), any(), any()))
                .thenReturn(List.of(new ReportUsageByPeriodResponseDTO(period, 8L, 3L)));

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso/todo").param("granularidad", "semana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalDevoluciones").value(3));
    }

    @Test
    void reportInventory_devuelve200() throws Exception {
        when(loanService.reportInventoryPaginated(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(inventory())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Clean Code"));
    }

    @Test
    void reportInventoryTodo_devuelve200() throws Exception {
        when(loanService.reportInventory(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(inventory()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isbn").value("9780132350884"));
    }

    @Test
    void reportOverdues_devuelve200() throws Exception {
        when(loanService.reportLoansOverduesPaginated(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(overdue())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].diasAtraso").value(5));
    }

    @Test
    void reportOverduesTodo_devuelve200() throws Exception {
        when(loanService.reportLoansOverdues(any(), any())).thenReturn(List.of(overdue()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prestamoId").value(1));
    }

    @Test
    void reportCategoriesDemanded_devuelve200() throws Exception {
        when(loanService.reportCategoriesDemandedPaginated(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(categoryDemanded())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoriaNombre").value("Ficción"));
    }

    @Test
    void reportCategoriesDemandedTodo_devuelve200() throws Exception {
        when(loanService.reportCategoriesDemanded(any(), any(), any()))
                .thenReturn(List.of(categoryDemanded()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPrestamos").value(20));
    }

    @Test
    void reportDelinquencyPdf_devuelve200() throws Exception {
        when(loanService.reportDelinquency(any())).thenReturn(List.of(delinquency()));
        when(reportPdfService.generateReportDelinquency(any())).thenReturn(new byte[]{1, 2});

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("reporte-morosidad.pdf")));
    }

    @Test
    void reportBooksMostLoanedPdf_devuelve200() throws Exception {
        when(loanService.reportBooksMostLoanedDetailed(any(), any(), any(), any()))
                .thenReturn(List.of(detailed()));
        when(reportPdfService.generateReportBooksMostLoaned(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reportInventoryPdf_devuelve200() throws Exception {
        when(loanService.reportInventory(any(), any(), any())).thenReturn(List.of(inventory()));
        when(reportPdfService.generateReportInventory(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reportOverduesPdf_devuelve200() throws Exception {
        when(loanService.reportLoansOverdues(any(), any())).thenReturn(List.of(overdue()));
        when(reportPdfService.generateReportOverdues(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reportCategoriesDemandedPdf_devuelve200() throws Exception {
        when(loanService.reportCategoriesDemanded(any(), any(), any()))
                .thenReturn(List.of(categoryDemanded()));
        when(reportPdfService.generateReportCategoriesDemanded(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reportUsagePdf_devuelve200() throws Exception {
        OffsetDateTime period = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(loanService.reportUsageByPeriod(any(), any(), any()))
                .thenReturn(List.of(new ReportUsageByPeriodResponseDTO(period, 1L, 1L)));
        when(reportPdfService.generateReportUsageByPeriod(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    private BookMostLoanedDetailedResponseDTO detailed() {
        return new BookMostLoanedDetailedResponseDTO(
                1L, "Clean Code", "9780132350884", "Uncle Bob", "Técnica", 12L, new BigDecimal("40.00"));
    }

    private ReportDelinquencyResponseDTO delinquency() {
        return new ReportDelinquencyResponseDTO(
                2L, "Ana", "Pérez", "lector@correo.com", new BigDecimal("5.00"), 1L, new BigDecimal("3.00"));
    }

    private ReportInventoryResponseDTO inventory() {
        return new ReportInventoryResponseDTO(
                1L, "Clean Code", "9780132350884", "Uncle Bob", "Técnica",
                (short) 3, (short) 1, "DISPONIBLE", "Prentice", "Prov", "Español", "ACTIVO",
                (short) 2008, "A1");
    }

    private ReportOverduesResponseDTO overdue() {
        return new ReportOverduesResponseDTO(
                1L, "Ana Pérez", "lector@correo.com", "Clean Code", "9780132350884",
                OffsetDateTime.parse("2026-01-10T10:00:00-05:00"), 5L, new BigDecimal("2.50"));
    }

    private ReportCategoriesDemandedResponseDTO categoryDemanded() {
        return new ReportCategoriesDemandedResponseDTO(1, "Ficción", 20L, new BigDecimal("50.00"));
    }
}
