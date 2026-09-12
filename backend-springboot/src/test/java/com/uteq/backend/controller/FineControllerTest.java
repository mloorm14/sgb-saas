package com.uteq.backend.controller;

import com.uteq.backend.dto.CancellationFineRequestDTO;
import com.uteq.backend.dto.FineActionResponseDTO;
import com.uteq.backend.dto.FineDetailResponseDTO;
import com.uteq.backend.dto.FineResponseDTO;
import com.uteq.backend.dto.PaymentFineRequestDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.FineService;
import com.uteq.backend.service.NotificationService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FineController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "gerente@correo.com", roles = "GERENTE")
class FineControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FineService fineService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ReportPdfService reportPdfService;

    @Test
    void listByUser_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(fineService.listByUser(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new FineResponseDTO(
                        1L, 10L, new BigDecimal("3.50"), 1, ahora, null, "atraso"))));

        mockMvc.perform(get("/api/v1/multas/usuario/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].monto").value(3.50));
    }

    @Test
    void listDetailByUser_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(fineService.listDetailByUser(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new FineDetailResponseDTO(
                        1L, 10L, "Clean Code", "9780132350884", "atraso",
                        new BigDecimal("3.50"), BigDecimal.ZERO, new BigDecimal("3.50"),
                        1, "PENDIENTE", ahora, null, ahora, ahora.plusDays(7), 2))));

        mockMvc.perform(get("/api/v1/multas/usuario/2/detalle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void pay_withoutBody_paymentFull_devuelve200() throws Exception {
        when(fineService.pay(1L)).thenReturn(new FineActionResponseDTO(1L, true));
        when(fineService.resolveUserIdFine(1L)).thenReturn(2L);

        mockMvc.perform(post("/api/v1/multas/1/pago"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.o_multa_id").value(1))
                .andExpect(jsonPath("$.o_estado").value("PAGADA"));

        verify(notificationService).notifyReceiptPayment(eq(2L), eq(1L), isNull());
    }

    @Test
    void pay_parcial_devuelve200() throws Exception {
        when(fineService.paymentParcial(eq(1L), eq(new BigDecimal("1.50"))))
                .thenReturn(Map.of("o_multa_id", 1L, "o_saldo_restante", new BigDecimal("2.00")));
        when(fineService.resolveUserIdFine(1L)).thenReturn(2L);

        mockMvc.perform(post("/api/v1/multas/1/pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PaymentFineRequestDTO(new BigDecimal("1.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.o_multa_id").value(1));
    }

    @Test
    void pay_inexistente_devuelve404() throws Exception {
        when(fineService.pay(99L)).thenThrow(new EntityNotFoundException("Multa no encontrada: 99"));

        mockMvc.perform(post("/api/v1/multas/99/pago"))
                .andExpect(status().isNotFound());
    }

    @Test
    void void_reasonValid_devuelve200() throws Exception {
        when(fineService.annul(eq(1L), eq("condonada"), any()))
                .thenReturn(new FineActionResponseDTO(1L, true));

        mockMvc.perform(post("/api/v1/multas/1/anulacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancellationFineRequestDTO("condonada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multaId").value(1));
    }

    @Test
    void void_withoutReason_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/multas/1/anulacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportSummaryFinancial_devuelve200() throws Exception {
        when(fineService.reportSummaryFinancial(any(), any()))
                .thenReturn(new SummaryFinancialFinesResponseDTO(
                        new BigDecimal("10.00"), new BigDecimal("4.00"), new BigDecimal("1.00"), List.of()));

        mockMvc.perform(get("/api/v1/multas/reportes/resumen-financiero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecaudado").value(10.00));
    }

    @Test
    void reportSummaryFinancialPdf_devuelve200() throws Exception {
        when(fineService.reportSummaryFinancial(any(), any()))
                .thenReturn(new SummaryFinancialFinesResponseDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        when(reportPdfService.generateReportSummaryFinancial(any())).thenReturn(new byte[]{9});

        mockMvc.perform(get("/api/v1/multas/reportes/resumen-financiero/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
