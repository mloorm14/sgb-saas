package com.uteq.backend.service;

import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
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
 * generado por {@link ReportPdfService} no está vacío y contiene el texto
 * esperado (PDFTextStripper) -- nunca en código de producción (ver
 * comentario de la dependencia en pom.xml, scope test).
 */
class ReportPdfServiceTest {

    private final ReportPdfService service = new ReportPdfService();

    // ── Test 1: PDF con filas contiene los datos esperados ──
    @Test
    void generateReportDelinquency_withRows_generaPdfWithDataEsperados() throws IOException {
        List<ReportDelinquencyResponseDTO> rows = List.of(
                new ReportDelinquencyResponseDTO(
                        1L, "Ana", "Pérez", "ana@correo.com",
                        new BigDecimal("15.50"), 2L, new BigDecimal("3.5"))
        );

        byte[] pdf = service.generateReportDelinquency(rows);

        assertThat(pdf).isNotEmpty();
        String text = extractText(pdf);
        assertThat(text).contains("Reporte de índice de morosidad");
        assertThat(text).contains("Ana Pérez");
        assertThat(text).contains("ana@correo.com");
    }

    // ── Test 2: PDF sin filas no está vacío y avisa "sin morosos" ──
    @Test
    void generateReportDelinquency_withoutRows_generaPdfWithMessageVacio() throws IOException {
        byte[] pdf = service.generateReportDelinquency(List.of());

        assertThat(pdf).isNotEmpty();
        String text = extractText(pdf);
        assertThat(text).contains("No hay usuarios con multas pendientes");
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
