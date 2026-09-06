package com.uteq.backend.controller;

import com.uteq.backend.dto.AnulacionMultaRequestDTO;
import com.uteq.backend.dto.MultaAccionResponseDTO;
import com.uteq.backend.dto.MultaDetalleResponseDTO;
import com.uteq.backend.dto.MultaResponseDTO;
import com.uteq.backend.dto.PagoMultaRequestDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.MultaService;
import com.uteq.backend.service.NotificacionService;
import com.uteq.backend.service.ReportePdfService;
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

@WebMvcTest(MultaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "gerente@correo.com", roles = "GERENTE")
class MultaControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MultaService multaService;

    @MockitoBean
    private NotificacionService notificacionService;

    @MockitoBean
    private ReportePdfService reportePdfService;

    @Test
    void listarPorUsuario_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(multaService.listarPorUsuario(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new MultaResponseDTO(
                        1L, 10L, new BigDecimal("3.50"), 1, ahora, null, "atraso"))));

        mockMvc.perform(get("/api/v1/multas/usuario/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].monto").value(3.50));
    }

    @Test
    void listarDetallePorUsuario_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(multaService.listarDetallePorUsuario(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new MultaDetalleResponseDTO(
                        1L, 10L, "Clean Code", "9780132350884", "atraso",
                        new BigDecimal("3.50"), BigDecimal.ZERO, new BigDecimal("3.50"),
                        1, "PENDIENTE", ahora, null, ahora, ahora.plusDays(7), 2))));

        mockMvc.perform(get("/api/v1/multas/usuario/2/detalle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void pagar_sinBody_pagoCompleto_devuelve200() throws Exception {
        when(multaService.pagar(1L)).thenReturn(new MultaAccionResponseDTO(1L, true));
        when(multaService.resolverUsuarioIdDeMulta(1L)).thenReturn(2L);

        mockMvc.perform(post("/api/v1/multas/1/pago"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.o_multa_id").value(1))
                .andExpect(jsonPath("$.o_estado").value("PAGADA"));

        verify(notificacionService).notificarComprobantePago(eq(2L), eq(1L), isNull());
    }

    @Test
    void pagar_parcial_devuelve200() throws Exception {
        when(multaService.pagoParcial(eq(1L), eq(new BigDecimal("1.50"))))
                .thenReturn(Map.of("o_multa_id", 1L, "o_saldo_restante", new BigDecimal("2.00")));
        when(multaService.resolverUsuarioIdDeMulta(1L)).thenReturn(2L);

        mockMvc.perform(post("/api/v1/multas/1/pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PagoMultaRequestDTO(new BigDecimal("1.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.o_multa_id").value(1));
    }

    @Test
    void pagar_inexistente_devuelve404() throws Exception {
        when(multaService.pagar(99L)).thenThrow(new EntityNotFoundException("Multa no encontrada: 99"));

        mockMvc.perform(post("/api/v1/multas/99/pago"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anular_motivoValido_devuelve200() throws Exception {
        when(multaService.anular(eq(1L), eq("condonada"), any()))
                .thenReturn(new MultaAccionResponseDTO(1L, true));

        mockMvc.perform(post("/api/v1/multas/1/anulacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnulacionMultaRequestDTO("condonada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.multaId").value(1));
    }

    @Test
    void anular_sinMotivo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/multas/1/anulacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteResumenFinanciero_devuelve200() throws Exception {
        when(multaService.reporteResumenFinanciero(any(), any()))
                .thenReturn(new ResumenFinancieroMultasResponseDTO(
                        new BigDecimal("10.00"), new BigDecimal("4.00"), new BigDecimal("1.00"), List.of()));

        mockMvc.perform(get("/api/v1/multas/reportes/resumen-financiero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecaudado").value(10.00));
    }

    @Test
    void reporteResumenFinancieroPdf_devuelve200() throws Exception {
        when(multaService.reporteResumenFinanciero(any(), any()))
                .thenReturn(new ResumenFinancieroMultasResponseDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        when(reportePdfService.generarReporteResumenFinanciero(any())).thenReturn(new byte[]{9});

        mockMvc.perform(get("/api/v1/multas/reportes/resumen-financiero/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }
}
