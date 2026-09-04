package com.uteq.backend.service;

import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDFBox se usa ÚNICAMENTE aquí, para verificar en el test que el PDF
 * generado por {@link ReportePdfService} no está vacío y contiene el texto
 * esperado (PDFTextStripper) -- nunca en código de producción (ver
 * comentario de la dependencia en pom.xml, scope test).
 */
class ReportePdfServiceTest {

    private final ReportePdfService service = new ReportePdfService();

    // ── Test 1: PDF con filas contiene los datos esperados ──
    @Test
    void generarReporteMorosidad_conFilas_generaPdfConDatosEsperados() throws IOException {
        List<ReporteMorosidadResponseDTO> filas = List.of(
                new ReporteMorosidadResponseDTO(
                        1L, "Ana", "Pérez", "ana@correo.com",
                        new BigDecimal("15.50"), 2L, new BigDecimal("3.5"))
        );

        byte[] pdf = service.generarReporteMorosidad(filas);

        assertThat(pdf).isNotEmpty();
        String texto = extraerTexto(pdf);
        assertThat(texto).contains("Reporte de índice de morosidad");
        assertThat(texto).contains("Ana Pérez");
        assertThat(texto).contains("ana@correo.com");
    }

    // ── Test 2: PDF sin filas no está vacío y avisa "sin morosos" ──
    @Test
    void generarReporteMorosidad_sinFilas_generaPdfConMensajeVacio() throws IOException {
        byte[] pdf = service.generarReporteMorosidad(List.of());

        assertThat(pdf).isNotEmpty();
        String texto = extraerTexto(pdf);
        assertThat(texto).contains("No hay usuarios con multas pendientes");
    }

    private String extraerTexto(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
