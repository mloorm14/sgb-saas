package com.uteq.backend.controller;

import com.uteq.backend.dto.DevolucionResponseDTO;
import com.uteq.backend.dto.LibroMasPrestadoResponseDTO;
import com.uteq.backend.dto.PrestamoActivoResponseDTO;
import com.uteq.backend.dto.PrestamoRequestDTO;
import com.uteq.backend.dto.PrestamoResponseDTO;
import com.uteq.backend.dto.RenovacionResponseDTO;
import com.uteq.backend.dto.ReporteMorosidadResponseDTO;
import com.uteq.backend.dto.ReporteUsoPorPeriodoResponseDTO;
import com.uteq.backend.service.PrestamoService;
import com.uteq.backend.service.ReportePdfService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/prestamos")
public class PrestamoController {

    private final PrestamoService prestamoService;
    private final ReportePdfService reportePdfService;

    public PrestamoController(PrestamoService prestamoService, ReportePdfService reportePdfService) {
        this.prestamoService = prestamoService;
        this.reportePdfService = reportePdfService;
    }

    // ── POST /api/v1/prestamos ────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<PrestamoResponseDTO> crear(
            @Valid @RequestBody PrestamoRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prestamoService.crear(dto, authentication));
    }

    // ── POST /api/v1/prestamos/{id}/devolucion ────────────
    @PostMapping("/{id}/devolucion")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<DevolucionResponseDTO> registrarDevolucion(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.registrarDevolucion(id));
    }

    // ── POST /api/v1/prestamos/{id}/renovacion ────────────
    // LECTOR solo su propio préstamo (verificado dentro de
    // PrestamoService.renovar()); BIBLIOTECARIO/GERENTE/ADMIN, cualquiera.
    @PostMapping("/{id}/renovacion")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    public ResponseEntity<RenovacionResponseDTO> renovar(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(prestamoService.renovar(id, authentication));
    }

    // ── GET /api/v1/prestamos/usuario/{usuarioId}?page=0&size=10 ──
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<Page<PrestamoResponseDTO>> listarPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "fechaPrestamo") Pageable pageable) {
        return ResponseEntity.ok(
                prestamoService.listarPorUsuario(usuarioId, authentication, pageable));
    }

    // ── GET /api/v1/prestamos/usuario/{usuarioId}/activos ─
    @GetMapping("/usuario/{usuarioId}/activos")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<List<PrestamoActivoResponseDTO>> listarActivosPorUsuario(
            @PathVariable Long usuarioId,
            Authentication authentication) {
        return ResponseEntity.ok(
                prestamoService.listarActivosPorUsuario(usuarioId, authentication));
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados ──
    @GetMapping("/reportes/libros-mas-prestados")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<List<LibroMasPrestadoResponseDTO>> reporteLibrosMasPrestados(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(
                prestamoService.reporteLibrosMasPrestados(limite, desde, hasta));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad ──────────
    // Mismo criterio de acceso que reporteLibrosMasPrestados (arriba):
    // BIBLIOTECARIO/GERENTE, no GERENTE/ADMIN -- se sigue el patrón que ya
    // existe en este controller en vez del roadmap original del Módulo 7
    // (que sugería GERENTE/ADMIN sin haber revisado el código real).
    @GetMapping("/reportes/morosidad")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<List<ReporteMorosidadResponseDTO>> reporteMorosidad(
            @RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(prestamoService.reporteMorosidad(limite));
    }

    // ── GET /api/v1/prestamos/reportes/uso?granularidad=dia|semana|mes ──
    @GetMapping("/reportes/uso")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<List<ReporteUsoPorPeriodoResponseDTO>> reporteUsoPorPeriodo(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(
                prestamoService.reporteUsoPorPeriodo(granularidad, desde, hasta));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad/pdf ──────
    // Exportación a PDF del mismo reporte de arriba (ReportePdfService),
    // transmitida directo en el cuerpo de la respuesta -- nunca se guarda
    // en disco del servidor (ver Javadoc de ReportePdfService).
    @GetMapping(value = "/reportes/morosidad/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<byte[]> reporteMorosidadPdf(
            @RequestParam(required = false) Integer limite) {
        List<ReporteMorosidadResponseDTO> reporte = prestamoService.reporteMorosidad(limite);
        byte[] pdf = reportePdfService.generarReporteMorosidad(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-morosidad.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
