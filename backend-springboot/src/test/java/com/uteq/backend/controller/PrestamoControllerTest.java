package com.uteq.backend.controller;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoDetalladoResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoResponseDTO;
import com.uteq.backend.dto.PrestamoActivoResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.dto.RenovacionResponseDTO;
import com.uteq.backend.dto.ReporteCategoriasDemandadasResponseDTO;
import com.uteq.backend.dto.ReporteInventarioResponseDTO;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import com.uteq.backend.dto.ReporteUsoPorPeriodoResponseDTO;
import com.uteq.backend.dto.ReporteVencidosResponseDTO;
import com.uteq.backend.exception.GlobalExceptionHandler;
import com.uteq.backend.service.PrestamoService;
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

@WebMvcTest(PrestamoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "admin@correo.com", roles = "ADMIN")
class PrestamoControllerTest extends WebMvcControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PrestamoService prestamoService;

    @MockitoBean
    private ReportePdfService reportePdfService;

    private PrestamoResponseDTO prestamo() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        return new PrestamoResponseDTO(1L, 2L, 3L, 4L, null, ahora, ahora.plusDays(7), null, (short) 0, 1);
    }

    @Test
    void crear_datosValidos_devuelve201() throws Exception {
        when(prestamoService.crear(any(), any())).thenReturn(prestamo());

        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrestamoRequestDTO(2L, null, 3L, 7, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.libroId").value(3));
    }

    @Test
    void crear_sinLibro_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/prestamos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":2,\"diasPrestamo\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarDevolucion_existente_devuelve200() throws Exception {
        when(prestamoService.registrarDevolucion(1L))
                .thenReturn(new DevolucionResponseDTO(1L, false, BigDecimal.ZERO));

        mockMvc.perform(post("/api/v1/prestamos/1/devolucion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestamoId").value(1));
    }

    @Test
    void registrarDevolucion_inexistente_devuelve404() throws Exception {
        when(prestamoService.registrarDevolucion(99L))
                .thenThrow(new EntityNotFoundException("Préstamo no encontrado: 99"));

        mockMvc.perform(post("/api/v1/prestamos/99/devolucion"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Préstamo no encontrado: 99"));
    }

    @Test
    void renovar_existente_devuelve200() throws Exception {
        OffsetDateTime nueva = OffsetDateTime.parse("2026-01-22T10:00:00-05:00");
        when(prestamoService.renovar(eq(1L), any()))
                .thenReturn(new RenovacionResponseDTO(1L, nueva, (short) 1, (short) 1));

        mockMvc.perform(post("/api/v1/prestamos/1/renovacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestamoId").value(1))
                .andExpect(jsonPath("$.renovacionesRealizadas").value(1));
    }

    @Test
    void listarPorUsuario_devuelve200() throws Exception {
        when(prestamoService.listarPorUsuario(eq(2L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(prestamo())));

        mockMvc.perform(get("/api/v1/prestamos/usuario/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioId").value(2));
    }

    @Test
    void listarActivosPorUsuario_devuelve200() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-01-15T10:00:00-05:00");
        when(prestamoService.listarActivosPorUsuario(eq(2L), any()))
                .thenReturn(List.of(new PrestamoActivoResponseDTO(
                        1L, "Clean Code", "9780132350884", ahora, ahora.plusDays(3), 3, "ACTIVO")));

        mockMvc.perform(get("/api/v1/prestamos/usuario/2/activos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].libroTitulo").value("Clean Code"));
    }

    @Test
    void reporteLibrosMasPrestados_devuelve200() throws Exception {
        when(prestamoService.reporteLibrosMasPrestados(any(), any(), any()))
                .thenReturn(List.of(new LibroMasPrestadoResponseDTO(1L, "Clean Code", "9780132350884", 12L)));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados").param("limite", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPrestamos").value(12));
    }

    @Test
    void reporteLibrosMasPrestadosDetallado_devuelve200() throws Exception {
        when(prestamoService.reporteLibrosMasPrestadosDetalladoPaginado(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(detallado())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados-detallado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Clean Code"));
    }

    @Test
    void reporteLibrosMasPrestadosDetalladoTodo_devuelve200() throws Exception {
        when(prestamoService.reporteLibrosMasPrestadosDetallado(any(), any(), any(), any()))
                .thenReturn(List.of(detallado()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados-detallado/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isbn").value("9780132350884"));
    }

    @Test
    void reporteMorosidad_devuelve200() throws Exception {
        when(prestamoService.reporteMorosidadPaginado(any(), any()))
                .thenReturn(new PageImpl<>(List.of(morosidad())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].correo").value("lector@correo.com"));
    }

    @Test
    void reporteMorosidadTodo_devuelve200() throws Exception {
        when(prestamoService.reporteMorosidad(any())).thenReturn(List.of(morosidad()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value(2));
    }

    @Test
    void reporteUsoPorPeriodo_devuelve200() throws Exception {
        OffsetDateTime periodo = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(prestamoService.reporteUsoPorPeriodoPaginado(eq("dia"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new ReporteUsoPorPeriodoResponseDTO(periodo, 4L, 2L))));

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso").param("granularidad", "dia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalPrestamos").value(4));
    }

    @Test
    void reporteUsoPorPeriodoTodo_devuelve200() throws Exception {
        OffsetDateTime periodo = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(prestamoService.reporteUsoPorPeriodo(eq("semana"), any(), any()))
                .thenReturn(List.of(new ReporteUsoPorPeriodoResponseDTO(periodo, 8L, 3L)));

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso/todo").param("granularidad", "semana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalDevoluciones").value(3));
    }

    @Test
    void reporteInventario_devuelve200() throws Exception {
        when(prestamoService.reporteInventarioPaginado(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(inventario())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Clean Code"));
    }

    @Test
    void reporteInventarioTodo_devuelve200() throws Exception {
        when(prestamoService.reporteInventario(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(inventario()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isbn").value("9780132350884"));
    }

    @Test
    void reporteVencidos_devuelve200() throws Exception {
        when(prestamoService.reportePrestamosVencidosPaginado(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(vencido())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].diasAtraso").value(5));
    }

    @Test
    void reporteVencidosTodo_devuelve200() throws Exception {
        when(prestamoService.reportePrestamosVencidos(any(), any())).thenReturn(List.of(vencido()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prestamoId").value(1));
    }

    @Test
    void reporteCategoriasDemandadas_devuelve200() throws Exception {
        when(prestamoService.reporteCategoriasDemandadasPaginado(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(categoriaDemandada())));

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoriaNombre").value("Ficción"));
    }

    @Test
    void reporteCategoriasDemandadasTodo_devuelve200() throws Exception {
        when(prestamoService.reporteCategoriasDemandadas(any(), any(), any()))
                .thenReturn(List.of(categoriaDemandada()));

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalPrestamos").value(20));
    }

    @Test
    void reporteMorosidadPdf_devuelve200() throws Exception {
        when(prestamoService.reporteMorosidad(any())).thenReturn(List.of(morosidad()));
        when(reportePdfService.generarReporteMorosidad(any())).thenReturn(new byte[]{1, 2});

        mockMvc.perform(get("/api/v1/prestamos/reportes/morosidad/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("reporte-morosidad.pdf")));
    }

    @Test
    void reporteLibrosMasPrestadosPdf_devuelve200() throws Exception {
        when(prestamoService.reporteLibrosMasPrestadosDetallado(any(), any(), any(), any()))
                .thenReturn(List.of(detallado()));
        when(reportePdfService.generarReporteLibrosMasPrestados(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/libros-mas-prestados/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reporteInventarioPdf_devuelve200() throws Exception {
        when(prestamoService.reporteInventario(any(), any(), any())).thenReturn(List.of(inventario()));
        when(reportePdfService.generarReporteInventario(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/inventario/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reporteVencidosPdf_devuelve200() throws Exception {
        when(prestamoService.reportePrestamosVencidos(any(), any())).thenReturn(List.of(vencido()));
        when(reportePdfService.generarReporteVencidos(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/vencidos/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reporteCategoriasDemandadasPdf_devuelve200() throws Exception {
        when(prestamoService.reporteCategoriasDemandadas(any(), any(), any()))
                .thenReturn(List.of(categoriaDemandada()));
        when(reportePdfService.generarReporteCategoriasDemandadas(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/categorias-demandadas/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void reporteUsoPdf_devuelve200() throws Exception {
        OffsetDateTime periodo = OffsetDateTime.parse("2026-01-01T00:00:00-05:00");
        when(prestamoService.reporteUsoPorPeriodo(any(), any(), any()))
                .thenReturn(List.of(new ReporteUsoPorPeriodoResponseDTO(periodo, 1L, 1L)));
        when(reportePdfService.generarReporteUsoPorPeriodo(any())).thenReturn(new byte[]{1});

        mockMvc.perform(get("/api/v1/prestamos/reportes/uso/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    private LibroMasPrestadoDetalladoResponseDTO detallado() {
        return new LibroMasPrestadoDetalladoResponseDTO(
                1L, "Clean Code", "9780132350884", "Uncle Bob", "Técnica", 12L, new BigDecimal("40.00"));
    }

    private ReporteMorosidadResponseDTO morosidad() {
        return new ReporteMorosidadResponseDTO(
                2L, "Ana", "Pérez", "lector@correo.com", new BigDecimal("5.00"), 1L, new BigDecimal("3.00"));
    }

    private ReporteInventarioResponseDTO inventario() {
        return new ReporteInventarioResponseDTO(
                1L, "Clean Code", "9780132350884", "Uncle Bob", "Técnica",
                (short) 3, (short) 1, "DISPONIBLE", "Prentice", "Prov", "Español", "ACTIVO",
                (short) 2008, "A1");
    }

    private ReporteVencidosResponseDTO vencido() {
        return new ReporteVencidosResponseDTO(
                1L, "Ana Pérez", "lector@correo.com", "Clean Code", "9780132350884",
                OffsetDateTime.parse("2026-01-10T10:00:00-05:00"), 5L, new BigDecimal("2.50"));
    }

    private ReporteCategoriasDemandadasResponseDTO categoriaDemandada() {
        return new ReporteCategoriasDemandadasResponseDTO(1, "Ficción", 20L, new BigDecimal("50.00"));
    }
}
