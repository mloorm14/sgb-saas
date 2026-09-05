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
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<PrestamoResponseDTO> crear(
            @Valid @RequestBody PrestamoRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prestamoService.crear(dto, authentication));
    }

    // ── POST /api/v1/prestamos/{id}/devolucion ────────────
    @PostMapping("/{id}/devolucion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
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
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<LibroMasPrestadoResponseDTO>> reporteLibrosMasPrestados(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(
                prestamoService.reporteLibrosMasPrestados(limite, desde, hasta));
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados-detallado ──
    @GetMapping("/reportes/libros-mas-prestados-detallado")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<LibroMasPrestadoDetalladoResponseDTO>> reporteLibrosMasPrestadosDetallado(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Integer categoriaId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                prestamoService.reporteLibrosMasPrestadosDetalladoPaginado(limite, desde, hasta, categoriaId, pageable));
    }

    @GetMapping("/reportes/libros-mas-prestados-detallado/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<LibroMasPrestadoDetalladoResponseDTO>> reporteLibrosMasPrestadosDetalladoTodo(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Integer categoriaId) {
        return ResponseEntity.ok(prestamoService.reporteLibrosMasPrestadosDetallado(limite, desde, hasta, categoriaId));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad ──────────
    @GetMapping("/reportes/morosidad")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReporteMorosidadResponseDTO>> reporteMorosidad(
            @RequestParam(required = false) Integer limite,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(prestamoService.reporteMorosidadPaginado(limite, pageable));
    }

    @GetMapping("/reportes/morosidad/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReporteMorosidadResponseDTO>> reporteMorosidadTodo(
            @RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(prestamoService.reporteMorosidad(limite));
    }

    // ── GET /api/v1/prestamos/reportes/uso?granularidad=dia|semana|mes ──
    @GetMapping("/reportes/uso")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReporteUsoPorPeriodoResponseDTO>> reporteUsoPorPeriodo(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(prestamoService.reporteUsoPorPeriodoPaginado(granularidad, desde, hasta, pageable));
    }

    @GetMapping("/reportes/uso/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReporteUsoPorPeriodoResponseDTO>> reporteUsoPorPeriodoTodo(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(prestamoService.reporteUsoPorPeriodo(granularidad, desde, hasta));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad/pdf ──────
    @GetMapping(value = "/reportes/morosidad/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteMorosidadPdf(
            @RequestParam(required = false) Integer limite) {
        List<ReporteMorosidadResponseDTO> reporte = prestamoService.reporteMorosidad(limite);
        byte[] pdf = reportePdfService.generarReporteMorosidad(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-morosidad.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados/pdf ──
    @GetMapping(value = "/reportes/libros-mas-prestados/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteLibrosMasPrestadosPdf(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(required = false) Integer categoriaId) {
        List<LibroMasPrestadoDetalladoResponseDTO> reporte =
                prestamoService.reporteLibrosMasPrestadosDetallado(limite, desde, hasta, categoriaId);
        byte[] pdf = reportePdfService.generarReporteLibrosMasPrestados(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-libros-prestados.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/inventario/pdf ─────
    @GetMapping(value = "/reportes/inventario/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteInventarioPdf(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String estadoStock,
            @RequestParam(required = false) String busqueda) {
        List<ReporteInventarioResponseDTO> reporte =
                prestamoService.reporteInventario(categoriaId, estadoStock, busqueda);
        byte[] pdf = reportePdfService.generarReporteInventario(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-inventario.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/vencidos/pdf ───────
    @GetMapping(value = "/reportes/vencidos/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteVencidosPdf(
            @RequestParam(required = false) Integer diasAtrasoMin,
            @RequestParam(required = false) String busqueda) {
        List<ReporteVencidosResponseDTO> reporte =
                prestamoService.reportePrestamosVencidos(diasAtrasoMin, busqueda);
        byte[] pdf = reportePdfService.generarReporteVencidos(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-vencidos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/categorias-demandadas/pdf ──
    @GetMapping(value = "/reportes/categorias-demandadas/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteCategoriasDemandadasPdf(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        List<ReporteCategoriasDemandadasResponseDTO> reporte =
                prestamoService.reporteCategoriasDemandadas(limite, desde, hasta);
        byte[] pdf = reportePdfService.generarReporteCategoriasDemandadas(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-categorias.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/uso/pdf ───────────
    @GetMapping(value = "/reportes/uso/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reporteUsoPdf(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        List<ReporteUsoPorPeriodoResponseDTO> reporte = prestamoService.reporteUsoPorPeriodo(granularidad, desde, hasta);
        byte[] pdf = reportePdfService.generarReporteUsoPorPeriodo(reporte);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-uso-periodo.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/inventario ─────────
    // Paginacion real + 8 filtros gerenciales (categoria/editorial/año/stock/ubicacion/proveedor/estado/idioma)
    @GetMapping("/reportes/inventario")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReporteInventarioResponseDTO>> reporteInventario(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String estadoStock,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Integer editorialId,
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) Integer estadoLibroId,
            @RequestParam(required = false) Integer idiomaId,
            @RequestParam(required = false) Short anioDesde,
            @RequestParam(required = false) Short anioHasta,
            @RequestParam(required = false) Short stockTotalMin,
            @RequestParam(required = false) Short stockTotalMax,
            @RequestParam(required = false) Short stockDispMin,
            @RequestParam(required = false) Short stockDispMax,
            @RequestParam(required = false) String ubicacion,
            @PageableDefault(size = 20, sort = "titulo") Pageable pageable) {
        return ResponseEntity.ok(prestamoService.reporteInventarioPaginado(
                categoriaId, estadoStock, busqueda, editorialId, proveedorId, estadoLibroId, idiomaId,
                anioDesde, anioHasta, stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, ubicacion, pageable));
    }

    @GetMapping("/reportes/inventario/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReporteInventarioResponseDTO>> reporteInventarioTodo(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String estadoStock,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Integer editorialId,
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(required = false) Integer estadoLibroId,
            @RequestParam(required = false) Integer idiomaId,
            @RequestParam(required = false) Short anioDesde,
            @RequestParam(required = false) Short anioHasta,
            @RequestParam(required = false) Short stockTotalMin,
            @RequestParam(required = false) Short stockTotalMax,
            @RequestParam(required = false) Short stockDispMin,
            @RequestParam(required = false) Short stockDispMax,
            @RequestParam(required = false) String ubicacion) {
        return ResponseEntity.ok(prestamoService.reporteInventario(
                categoriaId, estadoStock, busqueda, editorialId, proveedorId, estadoLibroId, idiomaId,
                anioDesde, anioHasta, stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, ubicacion));
    }

    // ── GET /api/v1/prestamos/reportes/vencidos ───────────
    @GetMapping("/reportes/vencidos")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReporteVencidosResponseDTO>> reportePrestamosVencidos(
            @RequestParam(required = false) Integer diasAtrasoMin,
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(prestamoService.reportePrestamosVencidosPaginado(diasAtrasoMin, busqueda, pageable));
    }

    @GetMapping("/reportes/vencidos/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReporteVencidosResponseDTO>> reportePrestamosVencidosTodo(
            @RequestParam(required = false) Integer diasAtrasoMin,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(prestamoService.reportePrestamosVencidos(diasAtrasoMin, busqueda));
    }

    // ── GET /api/v1/prestamos/reportes/categorias-demandadas ──
    @GetMapping("/reportes/categorias-demandadas")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReporteCategoriasDemandadasResponseDTO>> reporteCategoriasDemandadas(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(prestamoService.reporteCategoriasDemandadasPaginado(limite, desde, hasta, pageable));
    }

    @GetMapping("/reportes/categorias-demandadas/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReporteCategoriasDemandadasResponseDTO>> reporteCategoriasDemandadasTodo(
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return ResponseEntity.ok(prestamoService.reporteCategoriasDemandadas(limite, desde, hasta));
    }
}
