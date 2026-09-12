package com.uteq.backend.controller;

import com.uteq.backend.dto.LoanReturnResponseDTO;
import com.uteq.backend.dto.BookMostLoanedDetailedResponseDTO;
import com.uteq.backend.dto.BookMostLoanedResponseDTO;
import com.uteq.backend.dto.LoanActiveResponseDTO;
import com.uteq.backend.dto.LoanRequestDTO;
import com.uteq.backend.dto.LoanResponseDTO;
import com.uteq.backend.dto.RenewalResponseDTO;
import com.uteq.backend.dto.ReportCategoriesDemandedResponseDTO;
import com.uteq.backend.dto.ReportInventoryResponseDTO;
import com.uteq.backend.dto.ReportDelinquencyResponseDTO;
import com.uteq.backend.dto.ReportUsageByPeriodResponseDTO;
import com.uteq.backend.dto.ReportOverduesResponseDTO;
import com.uteq.backend.service.LoanService;
import com.uteq.backend.service.ReportPdfService;
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
public class LoanController {

    private final LoanService loanService;
    private final ReportPdfService reportPdfService;

    public LoanController(LoanService loanService, ReportPdfService reportPdfService) {
        this.loanService = loanService;
        this.reportPdfService = reportPdfService;
    }

    // ── POST /api/v1/prestamos ────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<LoanResponseDTO> create(
            @Valid @RequestBody LoanRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.create(dto, authentication));
    }

    // ── POST /api/v1/prestamos/{id}/devolucion ────────────
    @PostMapping("/{id}/devolucion")
    @PreAuthorize("hasAnyRole('BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Registra register loan return validando los datos de entrada antes de persistir cambios.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<LoanReturnResponseDTO> registerLoanReturn(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.registerLoanReturn(id));
    }

    // ── POST /api/v1/prestamos/{id}/renovacion ────────────
    // LECTOR solo su propio préstamo (verificado dentro de
    // PrestamoService.renovar()); BIBLIOTECARIO/GERENTE/ADMIN, cualquiera.
    @PostMapping("/{id}/renovacion")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE','ADMIN')")
    /**
     * Actualiza renew con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<RenewalResponseDTO> renew(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(loanService.renew(id, authentication));
    }

    // ── GET /api/v1/prestamos/usuario/{usuarioId}?page=0&size=10 ──
    /**
     * Consulta list by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/usuario/{usuarioId}")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    public ResponseEntity<Page<LoanResponseDTO>> listByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication,
            @PageableDefault(size = 10, sort = "dateLoan") Pageable pageable) {
        return ResponseEntity.ok(
                loanService.listByUser(userId, authentication, pageable));
    }

    // ── GET /api/v1/prestamos/usuario/{usuarioId}/activos ─
    @GetMapping("/usuario/{usuarioId}/activos")
    @PreAuthorize("hasAnyRole('LECTOR','BIBLIOTECARIO','GERENTE')")
    /**
     * Consulta list actives by user usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param userId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<List<LoanActiveResponseDTO>> listActivesByUser(
            @PathVariable("usuarioId") Long userId,
            Authentication authentication) {
        return ResponseEntity.ok(
                loanService.listActivesByUser(userId, authentication));
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados ──
    /**
     * Procesa report books most loaned y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/libros-mas-prestados")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<BookMostLoanedResponseDTO>> reportBooksMostLoaned(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        return ResponseEntity.ok(
                loanService.reportBooksMostLoaned(limit, from, until));
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados-detallado ──
    /**
     * Procesa report books most loaned detailed y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/libros-mas-prestados-detallado")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<BookMostLoanedDetailedResponseDTO>> reportBooksMostLoanedDetailed(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                loanService.reportBooksMostLoanedDetailedPaginated(limit, from, until, categoryId, pageable));
    }
    /**
     * Procesa report books most loaned detailed todo y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/libros-mas-prestados-detallado/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<BookMostLoanedDetailedResponseDTO>> reportBooksMostLoanedDetailedAll(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(name = "categoriaId", required = false) Integer categoryId) {
        return ResponseEntity.ok(loanService.reportBooksMostLoanedDetailed(limit, from, until, categoryId));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad ──────────
    /**
     * Procesa report delinquency y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/morosidad")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReportDelinquencyResponseDTO>> reportDelinquency(
            @RequestParam(name = "limite", required = false) Integer limit,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.reportDelinquencyPaginated(limit, pageable));
    }
    /**
     * Procesa report delinquency todo y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/morosidad/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReportDelinquencyResponseDTO>> reportDelinquencyAll(
            @RequestParam(name = "limite", required = false) Integer limit) {
        return ResponseEntity.ok(loanService.reportDelinquency(limit));
    }

    // ── GET /api/v1/prestamos/reportes/uso?granularidad=dia|semana|mes ──
    /**
     * Procesa report usage by period y devuelve el resultado calculado por el backend.
     *
     * @param granularidad criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/uso")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReportUsageByPeriodResponseDTO>> reportUsageByPeriod(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.reportUsageByPeriodPaginated(granularidad, from, until, pageable));
    }
    /**
     * Procesa report usage by period todo y devuelve el resultado calculado por el backend.
     *
     * @param granularidad criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/uso/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReportUsageByPeriodResponseDTO>> reportUsageByPeriodAll(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        return ResponseEntity.ok(loanService.reportUsageByPeriod(granularidad, from, until));
    }

    // ── GET /api/v1/prestamos/reportes/morosidad/pdf ──────
    /**
     * Procesa report delinquency pdf y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/morosidad/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportDelinquencyPdf(
            @RequestParam(name = "limite", required = false) Integer limit) {
        List<ReportDelinquencyResponseDTO> report = loanService.reportDelinquency(limit);
        byte[] pdf = reportPdfService.generateReportDelinquency(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-morosidad.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/libros-mas-prestados/pdf ──
    /**
     * Procesa report books most loaned pdf y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/libros-mas-prestados/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportBooksMostLoanedPdf(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(name = "categoriaId", required = false) Integer categoryId) {
        List<BookMostLoanedDetailedResponseDTO> report =
                loanService.reportBooksMostLoanedDetailed(limit, from, until, categoryId);
        byte[] pdf = reportPdfService.generateReportBooksMostLoaned(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-libros-prestados.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/inventario/pdf ─────
    /**
     * Procesa report inventory pdf y devuelve el resultado calculado por el backend.
     *
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param statusStock valor de entrada statusStock usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/inventario/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportInventoryPdf(
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @RequestParam(name = "estadoStock", required = false) String statusStock,
            @RequestParam(required = false) String busqueda) {
        List<ReportInventoryResponseDTO> report =
                loanService.reportInventory(categoryId, statusStock, busqueda);
        byte[] pdf = reportPdfService.generateReportInventory(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-inventario.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/vencidos/pdf ───────
    /**
     * Procesa report overdues pdf y devuelve el resultado calculado por el backend.
     *
     * @param daysAtrasoMin valor de entrada daysAtrasoMin usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/vencidos/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportOverduesPdf(
            @RequestParam(name = "diasAtrasoMin", required = false) Integer daysAtrasoMin,
            @RequestParam(required = false) String busqueda) {
        List<ReportOverduesResponseDTO> report =
                loanService.reportLoansOverdues(daysAtrasoMin, busqueda);
        byte[] pdf = reportPdfService.generateReportOverdues(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-vencidos.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/categorias-demandadas/pdf ──
    /**
     * Procesa report categories demanded pdf y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/categorias-demandadas/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportCategoriesDemandedPdf(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        List<ReportCategoriesDemandedResponseDTO> report =
                loanService.reportCategoriesDemanded(limit, from, until);
        byte[] pdf = reportPdfService.generateReportCategoriesDemanded(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-categorias.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/uso/pdf ───────────
    /**
     * Procesa report usage pdf y devuelve el resultado calculado por el backend.
     *
     * @param granularidad criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping(value = "/reportes/uso/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<byte[]> reportUsagePdf(
            @RequestParam(required = false, defaultValue = "dia") String granularidad,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        List<ReportUsageByPeriodResponseDTO> report = loanService.reportUsageByPeriod(granularidad, from, until);
        byte[] pdf = reportPdfService.generateReportUsageByPeriod(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte-uso-periodo.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /api/v1/prestamos/reportes/inventario ─────────
    // Paginacion real + 8 filtros gerenciales (categoria/editorial/año/stock/ubicacion/proveedor/estado/idioma)
    /**
     * Procesa report inventory y devuelve el resultado calculado por el backend.
     *
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param statusStock valor de entrada statusStock usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param publisherId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param supplierId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param statusBookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param languageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param yearFrom valor de entrada yearFrom usado por la operacion para completar su regla de negocio
     * @param yearUntil valor de entrada yearUntil usado por la operacion para completar su regla de negocio
     * @param stockTotalMin valor de entrada stockTotalMin usado por la operacion para completar su regla de negocio
     * @param stockTotalMax valor de entrada stockTotalMax usado por la operacion para completar su regla de negocio
     * @param stockDispMin valor de entrada stockDispMin usado por la operacion para completar su regla de negocio
     * @param stockDispMax valor de entrada stockDispMax usado por la operacion para completar su regla de negocio
     * @param location valor de entrada location usado por la operacion para completar su regla de negocio
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/inventario")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReportInventoryResponseDTO>> reportInventory(
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @RequestParam(name = "estadoStock", required = false) String statusStock,
            @RequestParam(required = false) String busqueda,
            @RequestParam(name = "editorialId", required = false) Integer publisherId,
            @RequestParam(name = "proveedorId", required = false) Integer supplierId,
            @RequestParam(name = "estadoLibroId", required = false) Integer statusBookId,
            @RequestParam(name = "idiomaId", required = false) Integer languageId,
            @RequestParam(name = "anioDesde", required = false) Short yearFrom,
            @RequestParam(name = "anioHasta", required = false) Short yearUntil,
            @RequestParam(required = false) Short stockTotalMin,
            @RequestParam(required = false) Short stockTotalMax,
            @RequestParam(required = false) Short stockDispMin,
            @RequestParam(required = false) Short stockDispMax,
            @RequestParam(name = "ubicacion", required = false) String location,
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return ResponseEntity.ok(loanService.reportInventoryPaginated(
                categoryId, statusStock, busqueda, publisherId, supplierId, statusBookId, languageId,
                yearFrom, yearUntil, stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, location, pageable));
    }
    /**
     * Procesa report inventory todo y devuelve el resultado calculado por el backend.
     *
     * @param categoryId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param statusStock valor de entrada statusStock usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param publisherId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param supplierId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param statusBookId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param languageId identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param yearFrom valor de entrada yearFrom usado por la operacion para completar su regla de negocio
     * @param yearUntil valor de entrada yearUntil usado por la operacion para completar su regla de negocio
     * @param stockTotalMin valor de entrada stockTotalMin usado por la operacion para completar su regla de negocio
     * @param stockTotalMax valor de entrada stockTotalMax usado por la operacion para completar su regla de negocio
     * @param stockDispMin valor de entrada stockDispMin usado por la operacion para completar su regla de negocio
     * @param stockDispMax valor de entrada stockDispMax usado por la operacion para completar su regla de negocio
     * @param location valor de entrada location usado por la operacion para completar su regla de negocio
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/inventario/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReportInventoryResponseDTO>> reportInventoryAll(
            @RequestParam(name = "categoriaId", required = false) Integer categoryId,
            @RequestParam(name = "estadoStock", required = false) String statusStock,
            @RequestParam(required = false) String busqueda,
            @RequestParam(name = "editorialId", required = false) Integer publisherId,
            @RequestParam(name = "proveedorId", required = false) Integer supplierId,
            @RequestParam(name = "estadoLibroId", required = false) Integer statusBookId,
            @RequestParam(name = "idiomaId", required = false) Integer languageId,
            @RequestParam(name = "anioDesde", required = false) Short yearFrom,
            @RequestParam(name = "anioHasta", required = false) Short yearUntil,
            @RequestParam(required = false) Short stockTotalMin,
            @RequestParam(required = false) Short stockTotalMax,
            @RequestParam(required = false) Short stockDispMin,
            @RequestParam(required = false) Short stockDispMax,
            @RequestParam(name = "ubicacion", required = false) String location) {
        return ResponseEntity.ok(loanService.reportInventory(
                categoryId, statusStock, busqueda, publisherId, supplierId, statusBookId, languageId,
                yearFrom, yearUntil, stockTotalMin, stockTotalMax, stockDispMin, stockDispMax, location));
    }

    // ── GET /api/v1/prestamos/reportes/vencidos ───────────
    /**
     * Procesa report loans overdues y devuelve el resultado calculado por el backend.
     *
     * @param daysAtrasoMin valor de entrada daysAtrasoMin usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/vencidos")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReportOverduesResponseDTO>> reportLoansOverdues(
            @RequestParam(name = "diasAtrasoMin", required = false) Integer daysAtrasoMin,
            @RequestParam(required = false) String busqueda,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.reportLoansOverduesPaginated(daysAtrasoMin, busqueda, pageable));
    }
    /**
     * Procesa report loans overdues todo y devuelve el resultado calculado por el backend.
     *
     * @param daysAtrasoMin valor de entrada daysAtrasoMin usado por la operacion para completar su regla de negocio
     * @param busqueda texto de busqueda o filtro usado para reducir los resultados devueltos
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/vencidos/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReportOverduesResponseDTO>> reportLoansOverduesAll(
            @RequestParam(name = "diasAtrasoMin", required = false) Integer daysAtrasoMin,
            @RequestParam(required = false) String busqueda) {
        return ResponseEntity.ok(loanService.reportLoansOverdues(daysAtrasoMin, busqueda));
    }

    // ── GET /api/v1/prestamos/reportes/categorias-demandadas ──
    /**
     * Procesa report categories demanded y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/reportes/categorias-demandadas")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<ReportCategoriesDemandedResponseDTO>> reportCategoriesDemanded(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(loanService.reportCategoriesDemandedPaginated(limit, from, until, pageable));
    }
    /**
     * Procesa report categories demanded todo y devuelve el resultado calculado por el backend.
     *
     * @param limit valor de entrada limit usado por la operacion para completar su regla de negocio
     * @param from fecha limite usada para acotar el rango temporal de la consulta
     * @param until fecha limite usada para acotar el rango temporal de la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */

    @GetMapping("/reportes/categorias-demandadas/todo")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<List<ReportCategoriesDemandedResponseDTO>> reportCategoriesDemandedAll(
            @RequestParam(name = "limite", required = false) Integer limit,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until) {
        return ResponseEntity.ok(loanService.reportCategoriesDemanded(limit, from, until));
    }
}
