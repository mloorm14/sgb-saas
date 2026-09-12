package com.uteq.backend.service;

import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.BookMostLoanedDetailedResponseDTO;
import com.uteq.backend.dto.RecentPaymentDTO;
import com.uteq.backend.dto.ReportCategoriesDemandedResponseDTO;
import com.uteq.backend.dto.ReportInventoryResponseDTO;
import com.uteq.backend.dto.ReportOverduesResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.dto.SuggestionGroupedDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    @Test
    void generateReportsCatalogos_withRows_incluyenDatosClave() throws IOException {
        String books = extractText(service.generateReportBooksMostLoaned(List.of(
                new BookMostLoanedDetailedResponseDTO(1L, "Clean Code", null, null, "Software", 4L, new BigDecimal("40.0"))
        )));
        String inventory = extractText(service.generateReportInventory(List.of(
                new ReportInventoryResponseDTO(1L, "Arquitectura limpia", null, null, "Software",
                        (short) 5, (short) 2, null, "Pearson", "Proveedor", "ES", "DISPONIBLE",
                        (short) 2018, "A1")
        )));
        String categories = extractText(service.generateReportCategoriesDemanded(List.of(
                new ReportCategoriesDemandedResponseDTO(2, "Software", 7L, new BigDecimal("70.0"))
        )));
        String suggestions = extractText(service.generateReportSuggestionsMostPedidas(List.of(
                new SuggestionGroupedDTO("9780132350884", "Refactoring", null, 3L)
        )));

        assertThat(books).contains("Reporte de libros más prestados", "Clean Code", "—");
        assertThat(inventory).contains("Reporte de inventario y disponibilidad", "Arquitectura limpia", "—");
        assertThat(categories).contains("Reporte de categorías más demandadas", "Software", "70.0%");
        assertThat(suggestions).contains("Reporte de sugerencias más pedidas", "Refactoring", "9780132350884");
    }

    @Test
    void generateReportsPrestamos_withDatesNullOrPresent_formateaYUsaVacio() throws IOException {
        OffsetDateTime date = OffsetDateTime.parse("2026-09-10T12:00:00Z");

        String overduesWithDate = extractText(service.generateReportOverdues(List.of(
                new ReportOverduesResponseDTO(10L, "Ana Pérez", "ana@correo.com", "Clean Code",
                        null, date, 3L, new BigDecimal("1.50"))
        )));
        String overduesWithoutDate = extractText(service.generateReportOverdues(List.of(
                new ReportOverduesResponseDTO(11L, "Luis Mora", "luis@correo.com", "Refactoring",
                        "ISBN-2", null, 0L, BigDecimal.ZERO)
        )));
        String usageWithDate = extractText(service.generateReportUsageByPeriod(List.of(
                new ReportUsageByPeriodResponseDTO(date, 5L, 4L)
        )));
        String usageWithoutDate = extractText(service.generateReportUsageByPeriod(List.of(
                new ReportUsageByPeriodResponseDTO(null, 0L, 0L)
        )));

        assertThat(overduesWithDate).contains("Reporte de préstamos vencidos activos", "Ana Pérez", "10/09/2026", "—");
        assertThat(overduesWithoutDate).contains("Luis Mora", "—");
        assertThat(usageWithDate).contains("Reporte de uso por período", "10/09/2026", "5");
        assertThat(usageWithoutDate).contains("—", "0");
    }

    @Test
    void generateReportSummaryFinancial_withAndWithoutPayments_cubreTablaYMensajeVacio() throws IOException {
        SummaryFinancialFinesResponseDTO withPayments = new SummaryFinancialFinesResponseDTO(
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                List.of(new RecentPaymentDTO(3L, new BigDecimal("4.50"),
                        OffsetDateTime.parse("2026-09-11T00:00:00Z"),
                        "ana@correo.com", null, null))
        );
        SummaryFinancialFinesResponseDTO withoutPayments = new SummaryFinancialFinesResponseDTO(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());

        String textWithPayments = extractText(service.generateReportSummaryFinancial(withPayments));
        String textWithoutPayments = extractText(service.generateReportSummaryFinancial(withoutPayments));

        assertThat(textWithPayments).contains("Resumen financiero de multas", "Recaudado: $20.00",
                "ana@correo.com", "—");
        assertThat(textWithoutPayments).contains("Sin pagos recientes.");
    }

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
