package com.uteq.backend.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.uteq.backend.dto.BookMostLoanedDetailedResponseDTO;
import com.uteq.backend.dto.ReportCategoriesDemandedResponseDTO;
import com.uteq.backend.dto.ReportInventoryResponseDTO;
import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.dto.ReportOverduesResponseDTO;
import com.uteq.backend.dto.SummaryFinancialFinesResponseDTO;
import com.uteq.backend.dto.SuggestionGroupedDTO;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class ReportPdfService {

    private static final DateTimeFormatter FORMATO_FECHA_GENERACION =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA_CORTA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TEXTO_VACIO = "—";
    private static final String HEADER_USUARIO = "Usuario";
    private static final String HEADER_PRESTAMOS = "Préstamos";
    private static final String HEADER_CATEGORIA = "Categoría";

    private static PdfFont createFuenteNegrita() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la fuente negrita del PDF", ex);
        }
    }

    private void agregarEncabezado(Document document, String title, PdfFont negrita) {
        document.add(new Paragraph("Leibri — Sistema de Gestión de Biblioteca")
                .setFont(negrita).setFontSize(14));
        document.add(new Paragraph(title).setFontSize(12));
        document.add(new Paragraph("Generado: " + OffsetDateTime.now().format(FORMATO_FECHA_GENERACION))
                .setFontSize(9));
        document.add(new Paragraph("\n"));
    }

    private Cell celdaEncabezado(String text, PdfFont negrita) {
        return new Cell()
                .add(new Paragraph(text).setFont(negrita).setFontSize(8))
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY);
    }

    private static String textOAlternativo(String value) {
        return value != null ? value : TEXTO_VACIO;
    }

    /** Primer valor no nulo, o el texto de vacío si todos son nulos. */
    private static String primeroNotNulo(String... values) {
        for (String v : values) {
            if (v != null) return v;
        }
        return TEXTO_VACIO;
    }

    /**
     * Agrega una tabla con headers al documento (sin encabezado general).
     * Permite reutilizar el trazado en reportes con cuerpo previo
     * (ej. resumen financiero con párrafos de totales).
     */
    private <T> void agregarTable(Document document, PdfFont negrita, float[] anchosColumns,
                                  List<String> headers, List<T> rows, BiConsumer<Table, T> agregarRow) {
        Table table = new Table(UnitValue.createPercentArray(anchosColumns)).useAllAvailableWidth();
        for (String header : headers) {
            table.addHeaderCell(celdaEncabezado(header, negrita));
        }
        for (T row : rows) {
            agregarRow.accept(table, row);
        }
        document.add(table);
    }

    /**
     * Genera un PDF estándar: encabezado + (mensaje de vacío | tabla).
     */
    private <T> byte[] generatePdf(String title, String messageVacio, List<T> rows,
                                  float[] anchosColumns, List<String> headers,
                                  BiConsumer<Table, T> agregarRow) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(output));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = createFuenteNegrita();
            agregarEncabezado(document, title, negrita);
            if (rows.isEmpty()) {
                document.add(new Paragraph(messageVacio));
            } else {
                agregarTable(document, negrita, anchosColumns, headers, rows, agregarRow);
            }
        }
        return output.toByteArray();
    }

    // ── Morosidad ─────────────────────────────────────────
    /**
         * Genera el reporte PDF de indice de morosidad.
     *
     * @param rows lista de ReportDelinquencyResponseDTO con la evidencia de multas.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportDelinquency(List<ReportDelinquencyResponseDTO> rows) {
        return generatePdf(
                "Reporte de índice de morosidad",
                "No hay usuarios con multas pendientes.",
                rows,
                new float[]{3, 3, 2, 2, 2},
                List.of(HEADER_USUARIO, "Correo", "Monto adeudado", "Multas pendientes", "Días atraso (prom.)"),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(f.name() + " " + f.lastName())));
                    table.addCell(new Cell().add(new Paragraph(f.email())));
                    table.addCell(new Cell().add(new Paragraph("$" + f.amountTotalAdeudado())));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.quantityFinesPendientes()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.daysAtrasoPromedio()))));
                });
    }

    // ── Libros más prestados ──────────────────────────────
    /**
         * Genera el reporte PDF de los libros mas prestados.
     *
     * @param rows lista de BookMostLoanedDetailedResponseDTO con los datos de prestamo.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportBooksMostLoaned(List<BookMostLoanedDetailedResponseDTO> rows) {
        int[] contador = {1};
        return generatePdf(
                "Reporte de libros más prestados",
                "No hay datos de préstamos.",
                rows,
                new float[]{1, 3, 2, 2, 2, 1.5f, 1.5f},
                List.of("#", "Título", "ISBN", "Autor", HEADER_CATEGORIA, HEADER_PRESTAMOS, "% del total"),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(contador[0]++))));
                    table.addCell(new Cell().add(new Paragraph(f.title())));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.isbn()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.authorName()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.categoryName()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalLoans()))));
                    table.addCell(new Cell().add(new Paragraph(f.percentage() + "%")));
                });
    }

    // ── Inventario ────────────────────────────────────────
    /**
         * Genera el reporte PDF de inventario y disponibilidad.
     *
     * @param rows lista de ReportInventoryResponseDTO con los datos de stock.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportInventory(List<ReportInventoryResponseDTO> rows) {
        return generatePdf(
                "Reporte de inventario y disponibilidad",
                "No hay datos de inventario.",
                rows,
                new float[]{3, 2, 2, 2, 1, 1, 2},
                List.of("Título", "ISBN", "Autor", HEADER_CATEGORIA, "Stock", "Disponible", "Estado"),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(f.title())));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.isbn()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.authorName()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.categoryName()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockTotal()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockAvailable()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.statusAvailability()))));
                });
    }

    // ── Préstamos vencidos ────────────────────────────────
    /**
         * Genera el reporte PDF de prestamos vencidos activos.
     *
     * @param rows lista de ReportOverduesResponseDTO con los datos de prestamos vencidos.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportOverdues(List<ReportOverduesResponseDTO> rows) {
        return generatePdf(
                "Reporte de préstamos vencidos activos",
                "No hay préstamos vencidos.",
                rows,
                new float[]{2.5f, 2.5f, 2.5f, 2, 2, 1, 1.5f},
                List.of(HEADER_USUARIO, "Correo", "Libro", "ISBN", "Vencimiento", "Días", "Multa est."),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(f.userName())));
                    table.addCell(new Cell().add(new Paragraph(f.userEmail())));
                    table.addCell(new Cell().add(new Paragraph(f.bookTitle())));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.bookIsbn()))));
                    table.addCell(new Cell().add(new Paragraph(f.dateLoanReturnEstimada() != null
                            ? f.dateLoanReturnEstimada().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO)));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.daysAtraso()))));
                    table.addCell(new Cell().add(new Paragraph("$" + f.amountFineEstimada())));
                });
    }

    // ── Uso por período ────────────────────────────────
    /**
         * Genera el reporte PDF de uso por periodo.
     *
     * @param rows lista de ReportUsageByPeriodResponseDTO con los datos de uso.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportUsageByPeriod(List<ReportUsageByPeriodResponseDTO> rows) {
        return generatePdf(
                "Reporte de uso por período",
                "No hay datos de uso por período.",
                rows,
                new float[]{3, 2, 2},
                List.of("Período", HEADER_PRESTAMOS, "Devoluciones"),
                (table, f) -> {
                    String period = f.period() != null ? f.period().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO;
                    table.addCell(new Cell().add(new Paragraph(period)));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalLoans()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalLoanReturns()))));
                });
    }

    // ── Resumen financiero ──────────────────────────────
    /**
         * Genera el reporte PDF resumen financiero de multas.
     *
     * @param dto SummaryFinancialFinesResponseDTO con los datos financieros.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportSummaryFinancial(SummaryFinancialFinesResponseDTO dto) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(output));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = createFuenteNegrita();
            agregarEncabezado(document, "Resumen financiero de multas", negrita);
            document.add(new Paragraph("Recaudado: $" + dto.totalRecaudado()).setFont(negrita));
            document.add(new Paragraph("Pendiente: $" + dto.totalPending()));
            document.add(new Paragraph("Generado hoy: $" + dto.totalGeneratedToday()));
            document.add(new Paragraph("\n"));
            if (dto.paymentsRecientes() == null || dto.paymentsRecientes().isEmpty()) {
                document.add(new Paragraph("Sin pagos recientes."));
            } else {
                agregarTable(
                        document,
                        negrita,
                        new float[]{1, 2, 2, 3, 3},
                        List.of("Multa", "Monto", "Fecha", HEADER_USUARIO, "Libro"),
                        dto.paymentsRecientes(),
                        (table, p) -> {
                            table.addCell(new Cell().add(new Paragraph(String.valueOf(p.fineId()))));
                            table.addCell(new Cell().add(new Paragraph("$" + p.amountPaid())));
                            table.addCell(new Cell().add(new Paragraph(p.datePaid() != null
                                    ? p.datePaid().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO)));
                            table.addCell(new Cell().add(new Paragraph(
                                    primeroNotNulo(p.userName(), p.userEmail()))));
                            table.addCell(new Cell().add(new Paragraph(p.bookTitle() != null ? p.bookTitle() : TEXTO_VACIO)));
                        });
            }
        }
        return output.toByteArray();
    }

    // ── Categorías demandadas ─────────────────────────────
    /**
         * Genera el reporte PDF de categorias mas demandadas.
     *
     * @param rows lista de ReportCategoriesDemandedResponseDTO con los datos de categorias.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportCategoriesDemanded(List<ReportCategoriesDemandedResponseDTO> rows) {
        int[] contador = {1};
        return generatePdf(
                "Reporte de categorías más demandadas",
                "No hay datos de categorías.",
                rows,
                new float[]{1, 4, 2, 2},
                List.of("#", HEADER_CATEGORIA, HEADER_PRESTAMOS, "% del total"),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(contador[0]++))));
                    table.addCell(new Cell().add(new Paragraph(f.categoryName())));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalLoans()))));
                    table.addCell(new Cell().add(new Paragraph(f.percentage() + "%")));
                });
    }

    // ── Sugerencias más pedidas ───────────────────────────
    /**
         * Genera el reporte PDF de sugerencias mas pedidas.
     *
     * @param rows lista de SuggestionGroupedDTO con los datos de solicitudes.
     * @return bytes con el PDF generado
     */
    public byte[] generateReportSuggestionsMostPedidas(List<SuggestionGroupedDTO> rows) {
        int[] contador = {1};
        return generatePdf(
                "Reporte de sugerencias más pedidas",
                "No hay sugerencias pendientes.",
                rows,
                new float[]{1, 4, 3, 2, 2},
                List.of("#", "Título", "Autor", "ISBN", "Solicitudes"),
                (table, f) -> {
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(contador[0]++))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.title()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.author()))));
                    table.addCell(new Cell().add(new Paragraph(textOAlternativo(f.isbn()))));
                    table.addCell(new Cell().add(new Paragraph(String.valueOf(f.quantity()))));
                });
    }
}
