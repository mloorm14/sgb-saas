package com.uteq.backend.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera el PDF del reporte de morosidad (Módulo 7.2 del roadmap) con iText
 * (dependencia {@code itext-core}, AGPL -- ver comentario en {@code pom.xml}).
 * <p>
 * El documento se arma completo en memoria sobre un
 * {@link ByteArrayOutputStream} y se retorna como {@code byte[]}: nunca se
 * escribe a disco del servidor, para no dejar archivos residuales entre
 * ejecuciones concurrentes de distintos usuarios pidiendo el mismo reporte
 * (requisito explícito del roadmap para este servicio). El controller
 * ({@code PrestamoController.reporteMorosidadPdf}) transmite ese arreglo
 * directo en el cuerpo de la respuesta HTTP.
 */
@Service
public class ReportePdfService {

    private static final DateTimeFormatter FORMATO_FECHA_GENERACION =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generarReporteMorosidad(List<ReporteMorosidadResponseDTO> filas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(salida));
             Document document = new Document(pdfDoc)) {

            document.add(new Paragraph("Sistema de Gestión de Biblioteca — UTEQ")
                    .setBold()
                    .setFontSize(14));
            document.add(new Paragraph("Reporte de índice de morosidad")
                    .setFontSize(12));
            document.add(new Paragraph("Generado: " + OffsetDateTime.now().format(FORMATO_FECHA_GENERACION))
                    .setFontSize(9));
            document.add(new Paragraph("\n"));

            if (filas.isEmpty()) {
                document.add(new Paragraph("No hay usuarios con multas pendientes."));
            } else {
                document.add(construirTabla(filas));
            }
        }

        return salida.toByteArray();
    }

    private Table construirTabla(List<ReporteMorosidadResponseDTO> filas) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{3, 3, 2, 2, 2}))
                .useAllAvailableWidth();

        tabla.addHeaderCell(celdaEncabezado("Usuario"));
        tabla.addHeaderCell(celdaEncabezado("Correo"));
        tabla.addHeaderCell(celdaEncabezado("Monto adeudado"));
        tabla.addHeaderCell(celdaEncabezado("Multas pendientes"));
        tabla.addHeaderCell(celdaEncabezado("Días de atraso (prom.)"));

        for (ReporteMorosidadResponseDTO fila : filas) {
            tabla.addCell(new Cell().add(new Paragraph(fila.nombre() + " " + fila.apellido())));
            tabla.addCell(new Cell().add(new Paragraph(fila.correo())));
            tabla.addCell(new Cell().add(new Paragraph("$" + fila.montoTotalAdeudado())));
            tabla.addCell(new Cell().add(new Paragraph(String.valueOf(fila.cantidadMultasPendientes()))));
            tabla.addCell(new Cell().add(new Paragraph(String.valueOf(fila.diasAtrasoPromedio()))));
        }

        return tabla;
    }

    private Cell celdaEncabezado(String texto) {
        return new Cell()
                .add(new Paragraph(texto).setBold())
                .setTextAlignment(TextAlignment.CENTER);
    }
}
