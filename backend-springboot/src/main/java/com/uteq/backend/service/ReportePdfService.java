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

@Service
public class ReportePdfService {

    private static final DateTimeFormatter FORMATO_FECHA_GENERACION =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static PdfFont crearFuenteNegrita() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la fuente negrita del PDF", ex);
        }
    }

    private static PdfFont crearFuenteRegular() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo cargar la fuente del PDF", ex);
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

    // ── Morosidad ─────────────────────────────────────────
    public byte[] generarReporteMorosidad(List<ReporteMorosidadResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de índice de morosidad", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay usuarios con multas pendientes."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{3, 3, 2, 2, 2})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("Usuario", negrita));
                tabla.addHeaderCell(celdaEncabezado("Correo", negrita));
                tabla.addHeaderCell(celdaEncabezado("Monto adeudado", negrita));
                tabla.addHeaderCell(celdaEncabezado("Multas pendientes", negrita));
                tabla.addHeaderCell(celdaEncabezado("Días atraso (prom.)", negrita));
                for (var f : filas) {
                    tabla.addCell(new Cell().add(new Paragraph(f.nombre() + " " + f.apellido())));
                    tabla.addCell(new Cell().add(new Paragraph(f.correo())));
                    tabla.addCell(new Cell().add(new Paragraph("$" + f.montoTotalAdeudado())));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.cantidadMultasPendientes()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.diasAtrasoPromedio()))));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }

    // ── Libros más prestados ──────────────────────────────
    public byte[] generarReporteLibrosMasPrestados(List<LibroMasPrestadoDetalladoResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de libros más prestados", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay datos de préstamos."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2, 2, 2, 1.5f, 1.5f})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("#", negrita));
                tabla.addHeaderCell(celdaEncabezado("Título", negrita));
                tabla.addHeaderCell(celdaEncabezado("ISBN", negrita));
                tabla.addHeaderCell(celdaEncabezado("Autor", negrita));
                tabla.addHeaderCell(celdaEncabezado("Categoría", negrita));
                tabla.addHeaderCell(celdaEncabezado("Préstamos", negrita));
                tabla.addHeaderCell(celdaEncabezado("% del total", negrita));
                int i = 1;
                for (var f : filas) {
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(i++))));
                    tabla.addCell(new Cell().add(new Paragraph(f.titulo())));
                    tabla.addCell(new Cell().add(new Paragraph(f.isbn() != null ? f.isbn() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(f.autorNombre() != null ? f.autorNombre() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(f.categoriaNombre() != null ? f.categoriaNombre() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.porcentaje() + "%")));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }

    // ── Inventario ────────────────────────────────────────
    public byte[] generarReporteInventario(List<ReporteInventarioResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de inventario y disponibilidad", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay datos de inventario."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 2, 1, 1, 2})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("Título", negrita));
                tabla.addHeaderCell(celdaEncabezado("ISBN", negrita));
                tabla.addHeaderCell(celdaEncabezado("Autor", negrita));
                tabla.addHeaderCell(celdaEncabezado("Categoría", negrita));
                tabla.addHeaderCell(celdaEncabezado("Stock", negrita));
                tabla.addHeaderCell(celdaEncabezado("Disponible", negrita));
                tabla.addHeaderCell(celdaEncabezado("Estado", negrita));
                for (var f : filas) {
                    tabla.addCell(new Cell().add(new Paragraph(f.titulo())));
                    tabla.addCell(new Cell().add(new Paragraph(f.isbn() != null ? f.isbn() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(f.autorNombre() != null ? f.autorNombre() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(f.categoriaNombre() != null ? f.categoriaNombre() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockTotal()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.stockDisponible()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.estadoDisponibilidad() != null ? f.estadoDisponibilidad() : "—")));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }

    // ── Préstamos vencidos ────────────────────────────────
    public byte[] generarReporteVencidos(List<ReporteVencidosResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de préstamos vencidos activos", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay préstamos vencidos."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{2.5f, 2.5f, 2.5f, 2, 2, 1, 1.5f})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("Usuario", negrita));
                tabla.addHeaderCell(celdaEncabezado("Correo", negrita));
                tabla.addHeaderCell(celdaEncabezado("Libro", negrita));
                tabla.addHeaderCell(celdaEncabezado("ISBN", negrita));
                tabla.addHeaderCell(celdaEncabezado("Vencimiento", negrita));
                tabla.addHeaderCell(celdaEncabezado("Días", negrita));
                tabla.addHeaderCell(celdaEncabezado("Multa est.", negrita));
                for (var f : filas) {
                    tabla.addCell(new Cell().add(new Paragraph(f.usuarioNombre())));
                    tabla.addCell(new Cell().add(new Paragraph(f.usuarioCorreo())));
                    tabla.addCell(new Cell().add(new Paragraph(f.libroTitulo())));
                    tabla.addCell(new Cell().add(new Paragraph(f.libroIsbn() != null ? f.libroIsbn() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(f.fechaDevolucionEstimada() != null
                            ? f.fechaDevolucionEstimada().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.diasAtraso()))));
                    tabla.addCell(new Cell().add(new Paragraph("$" + f.montoMultaEstimada())));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }

    // ── Uso por período ────────────────────────────────
    public byte[] generarReporteUsoPorPeriodo(List<ReporteUsoPorPeriodoResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de uso por período", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay datos de uso por período."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("Período", negrita));
                tabla.addHeaderCell(celdaEncabezado("Préstamos", negrita));
                tabla.addHeaderCell(celdaEncabezado("Devoluciones", negrita));
                for (var f : filas) {
                    String periodo = f.periodo() != null ? f.periodo().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—";
                    tabla.addCell(new Cell().add(new Paragraph(periodo)));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalDevoluciones()))));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
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
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{1, 2, 2, 3, 3})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("Multa", negrita));
                tabla.addHeaderCell(celdaEncabezado("Monto", negrita));
                tabla.addHeaderCell(celdaEncabezado("Fecha", negrita));
                tabla.addHeaderCell(celdaEncabezado("Usuario", negrita));
                tabla.addHeaderCell(celdaEncabezado("Libro", negrita));
                for (var p : dto.pagosRecientes()) {
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(p.multaId()))));
                    tabla.addCell(new Cell().add(new Paragraph("$" + p.montoPagado())));
                    tabla.addCell(new Cell().add(new Paragraph(p.fechaPagada() != null ? p.fechaPagada().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(p.usuarioNombre() != null ? p.usuarioNombre() : p.usuarioCorreo() != null ? p.usuarioCorreo() : "—")));
                    tabla.addCell(new Cell().add(new Paragraph(p.libroTitulo() != null ? p.libroTitulo() : "—")));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }

    // ── Categorías demandadas ─────────────────────────────
    public byte[] generarReporteCategoriasDemandadas(List<ReporteCategoriasDemandadasResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {
            PdfFont negrita = crearFuenteNegrita();
            agregarEncabezado(document, "Reporte de categorías más demandadas", negrita);
            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay datos de categorías."));
            } else {
                Table tabla = new Table(UnitValue.createPercentArray(new float[]{1, 4, 2, 2})).useAllAvailableWidth();
                tabla.addHeaderCell(celdaEncabezado("#", negrita));
                tabla.addHeaderCell(celdaEncabezado("Categoría", negrita));
                tabla.addHeaderCell(celdaEncabezado("Préstamos", negrita));
                tabla.addHeaderCell(celdaEncabezado("% del total", negrita));
                int i = 1;
                for (var f : filas) {
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(i++))));
                    tabla.addCell(new Cell().add(new Paragraph(f.categoriaNombre())));
                    tabla.addCell(new Cell().add(new Paragraph(String.valueOf(f.totalPrestamos()))));
                    tabla.addCell(new Cell().add(new Paragraph(f.porcentaje() + "%")));
                }
                document.add(tabla);
            }
        }
        return salida.toByteArray();
    }
}
