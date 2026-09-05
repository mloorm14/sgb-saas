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
import com.uteq.backend.dto.LibroMasPrestadoDetalladoResponseDTO;
import com.uteq.backend.dto.ReporteCategoriasDemandadasResponseDTO;
import com.uteq.backend.dto.ReporteInventarioResponseDTO;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import com.uteq.backend.dto.ReporteUsoPorPeriodoResponseDTO;
import com.uteq.backend.dto.ReporteVencidosResponseDTO;
import com.uteq.backend.dto.ResumenFinancieroMultasResponseDTO;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class ReportePdfService {

    private static final DateTimeFormatter FORMATO_FECHA_GENERACION =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA_CORTA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String TEXTO_VACIO = "—";
    private static final String HEADER_USUARIO = "Usuario";
    private static final String HEADER_PRESTAMOS = "Préstamos";
    private static final String HEADER_CATEGORIA = "Categoría";

    private static PdfFont crearFuenteNegrita() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la fuente negrita del PDF", ex);
        }
    }

    private void agregarEncabezado(Document document, String titulo, PdfFont negrita) {
        document.add(new Paragraph("Leibri — Sistema de Gestión de Biblioteca")
                .setFont(negrita).setFontSize(14));
        document.add(new Paragraph(titulo).setFontSize(12));
        document.add(new Paragraph("Generado: " + OffsetDateTime.now().format(FORMATO_FECHA_GENERACION))
                .setFontSize(9));
        document.add(new Paragraph("\n"));
    }

    private Cell celdaEncabezado(String texto, PdfFont negrita) {
        return new Cell()
                .add(new Paragraph(texto).setFont(negrita).setFontSize(8))
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY);
    }

    private static String textoOAlternativo(String valor) {
        return valor != null ? valor : TEXTO_VACIO;
    }

    /** Primer valor no nulo, o el texto de vacío si todos son nulos. */
    private static String primeroNoNulo(String... valores) {
        for (String v : valores) {
            if (v != null) return v;
        }
        return TEXTO_VACIO;
    }

    /**
     * Agrega una tabla con headers al documento (sin encabezado general).
     * Permite reutilizar el trazado en reportes con cuerpo previo
     * (ej. resumen financiero con párrafos de totales).
     */
    private <T> void agregarTabla(Document document, PdfFont negrita, float[] anchosColumnas,
                                  List<String> headers, List<T> filas, BiConsumer<Table, T> agregarFila) {
        Table tabla = new Table(UnitValue.createPercentArray(anchosColumnas)).useAllAvailableWidth();
        for (String header : headers) {
            tabla.addHeaderCell(celdaEncabezado(header, negrita));
        }
        for (T fila : filas) {
            agregarFila.accept(tabla, fila);
        }
        document.add(tabla);
    }

    /**
     * Genera un PDF estándar: encabezado + (mensaje de vacío | tabla).
     */
    private <T> byte[] generarPdf(String titulo, String mensajeVacio, List<T> filas,
                                  float[] anchosColumnas, List<String> headers,
                                  BiConsumer<Table, T> agregarFila) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, titulo, negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph(mensajeVacio));
            } else {
                agregarTabla(document, negrita, anchosColumnas, headers, filas, agregarFila);
            }
        }
        return salida.toByteArray();
    }

    // ── Morosidad ─────────────────────────────────────────
    public byte[] generarReporteMorosidad(List<ReporteMorosidadResponseDTO> filas) {
        return generarPdf(
                "Reporte de índice de morosidad",
                "No hay usuarios con multas pendientes.",
                filas,
                new float[]{3, 3, 2, 2, 2},
                List.of(HEADER_USUARIO, "Correo", "Monto adeudado", "Multas pendientes", "Días atraso (prom.)"),
                (tabla, f) -> {
                    tabla.addCell(new Cell().add(new Paragraph(f.nombre() + " " + f.apellido())));
                    tabla.addCell(new Cell().add(new Paragraph(f.correo())));
                    tabla.addCell(new Cell().add(new Paragraph("$" + f.montoTotalAdeudado())));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.cantidadMultasPendientes()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.diasAtrasoPromedio()))));
                });
    }

    // ── Libros más prestados ──────────────────────────────
    public byte[] generarReporteLibrosMasPrestados(List<LibroMasPrestadoDetalladoResponseDTO> filas) {
        int[] contador = {1};
        return generarPdf(
                "Reporte de libros más prestados",
                "No hay datos de préstamos.",
                filas,
                new float[]{1, 3, 2, 2, 2, 1.5f, 1.5f},
                List.of("#", "Título", "ISBN", "Autor", HEADER_CATEGORIA, HEADER_PRESTAMOS, "% del total"),
                (tabla, f) -> {
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(contador[0]++))));
                    tabla.addCell(new Cell().add(new Paragraph(f.titulo())));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.isbn()))));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.autorNombre()))));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.categoriaNombre()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.porcentaje() + "%")));
                });
    }

    // ── Inventario ────────────────────────────────────────
    public byte[] generarReporteInventario(List<ReporteInventarioResponseDTO> filas) {
        return generarPdf(
                "Reporte de inventario y disponibilidad",
                "No hay datos de inventario.",
                filas,
                new float[]{3, 2, 2, 2, 1, 1, 2},
                List.of("Título", "ISBN", "Autor", HEADER_CATEGORIA, "Stock", "Disponible", "Estado"),
                (tabla, f) -> {
                    tabla.addCell(new Cell().add(new Paragraph(f.titulo())));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.isbn()))));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.autorNombre()))));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.categoriaNombre()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockTotal()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockDisponible()))));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.estadoDisponibilidad()))));
                });
    }

    // ── Préstamos vencidos ────────────────────────────────
    public byte[] generarReporteVencidos(List<ReporteVencidosResponseDTO> filas) {
        return generarPdf(
                "Reporte de préstamos vencidos activos",
                "No hay préstamos vencidos.",
                filas,
                new float[]{2.5f, 2.5f, 2.5f, 2, 2, 1, 1.5f},
                List.of(HEADER_USUARIO, "Correo", "Libro", "ISBN", "Vencimiento", "Días", "Multa est."),
                (tabla, f) -> {
                    tabla.addCell(new Cell().add(new Paragraph(f.usuarioNombre())));
                    tabla.addCell(new Cell().add(new Paragraph(f.usuarioCorreo())));
                    tabla.addCell(new Cell().add(new Paragraph(f.libroTitulo())));
                    tabla.addCell(new Cell().add(new Paragraph(textoOAlternativo(f.libroIsbn()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.fechaDevolucionEstimada() != null
                            ? f.fechaDevolucionEstimada().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO)));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.diasAtraso()))));
                    tabla.addCell(new Cell().add(new Paragraph("$" + f.montoMultaEstimada())));
                });
    }

    // ── Uso por período ────────────────────────────────
    public byte[] generarReporteUsoPorPeriodo(List<ReporteUsoPorPeriodoResponseDTO> filas) {
        return generarPdf(
                "Reporte de uso por período",
                "No hay datos de uso por período.",
                filas,
                new float[]{3, 2, 2},
                List.of("Período", HEADER_PRESTAMOS, "Devoluciones"),
                (tabla, f) -> {
                    String periodo = f.periodo() != null ? f.periodo().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO;
                    tabla.addCell(new Cell().add(new Paragraph(periodo)));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalDevoluciones()))));
                });
    }

    // ── Resumen financiero ──────────────────────────────
    public byte[] generarReporteResumenFinanciero(ResumenFinancieroMultasResponseDTO dto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Resumen financiero de multas", negrita);
            document.add(new Paragraph("Recaudado: $" + dto.totalRecaudado()).setFont(negrita));
            document.add(new Paragraph("Pendiente: $" + dto.totalPendiente()));
            document.add(new Paragraph("Generado hoy: $" + dto.totalGeneradoHoy()));
            document.add(new Paragraph("\n"));
            if (dto.pagosRecientes() == null || dto.pagosRecientes().isEmpty()) {
                document.add(new Paragraph("Sin pagos recientes."));
            } else {
                agregarTabla(
                        document,
                        negrita,
                        new float[]{1, 2, 2, 3, 3},
                        List.of("Multa", "Monto", "Fecha", HEADER_USUARIO, "Libro"),
                        dto.pagosRecientes(),
                        (tabla, p) -> {
                            tabla.addCell(new Cell().add(new Paragraph(String.valueOf(p.multaId()))));
                            tabla.addCell(new Cell().add(new Paragraph("$" + p.montoPagado())));
                            tabla.addCell(new Cell().add(new Paragraph(p.fechaPagada() != null
                                    ? p.fechaPagada().format(FORMATO_FECHA_CORTA) : TEXTO_VACIO)));
                            tabla.addCell(new Cell().add(new Paragraph(
                                    primeroNoNulo(p.usuarioNombre(), p.usuarioCorreo()))));
                            tabla.addCell(new Cell().add(new Paragraph(p.libroTitulo() != null ? p.libroTitulo() : TEXTO_VACIO)));
                        });
            }
        }
        return salida.toByteArray();
    }

    // ── Categorías demandadas ─────────────────────────────
    public byte[] generarReporteCategoriasDemandadas(List<ReporteCategoriasDemandadasResponseDTO> filas) {
        int[] contador = {1};
        return generarPdf(
                "Reporte de categorías más demandadas",
                "No hay datos de categorías.",
                filas,
                new float[]{1, 4, 2, 2},
                List.of("#", HEADER_CATEGORIA, HEADER_PRESTAMOS, "% del total"),
                (tabla, f) -> {
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(contador[0]++))));
                    tabla.addCell(new Cell().add(new Paragraph(f.categoriaNombre())));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.porcentaje() + "%")));
                });
    }
}
