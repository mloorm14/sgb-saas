package com.uteq.backend.controller;

import com.uteq.backend.dto.ChangeStatusSuggestionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionRequestDTO;
import com.uteq.backend.dto.SuggestionAcquisitionResponseDTO;
import com.uteq.backend.dto.SuggestionGroupedDTO;
import com.uteq.backend.service.ReportPdfService;
import com.uteq.backend.service.SuggestionAcquisitionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Lector crea, gerente lista y cambia estado. LECTOR ve solo las
// suyas; GERENTE/ADMIN ven todas.
@RestController
@RequestMapping("/api/v1/sugerencias-adquisicion")
public class SuggestionAcquisitionController {

    private final SuggestionAcquisitionService suggestionService;
    private final ReportPdfService reportPdfService;

    public SuggestionAcquisitionController(SuggestionAcquisitionService suggestionService,
                                           ReportPdfService reportPdfService) {
        this.suggestionService = suggestionService;
        this.reportPdfService = reportPdfService;
    }

    // ── POST /api/v1/sugerencias-adquisicion ──────────────
    @PostMapping
    @PreAuthorize("hasRole('LECTOR')")
    /**
     * Registra create validando los datos de entrada antes de persistir cambios.
     *
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<SuggestionAcquisitionResponseDTO> create(
            @Valid @RequestBody SuggestionAcquisitionRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suggestionService.create(dto, authentication));
    }

    // ── GET /api/v1/sugerencias-adquisicion/mias ──────────
    /**
     * Consulta list owns usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/mias")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Page<SuggestionAcquisitionResponseDTO>> listOwns(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "created") Pageable pageable) {
        return ResponseEntity.ok(suggestionService.listOwns(authentication, pageable));
    }

    // ── GET /api/v1/sugerencias-adquisicion?estado=PENDIENTE ──
    /**
     * Consulta list todas usando los filtros recibidos y devuelve el resultado solicitado.
     *
     * @param status criterio de clasificacion usado para seleccionar la variante o filtro requerido
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SuggestionAcquisitionResponseDTO>> listAll(
            @RequestParam(name = "estado", required = false) String status,
            @PageableDefault(size = 10, sort = "created") Pageable pageable) {
        return ResponseEntity.ok(suggestionService.listAll(status, pageable));
    }

    // ── PATCH /api/v1/sugerencias-adquisicion/{id}/estado ─
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Actualiza change status con las reglas de negocio requeridas por el flujo.
     *
     * @param id identificador del registro que se usa para ubicar el recurso en la base de datos
     * @param dto datos validados de la peticion con la informacion necesaria para ejecutar la operacion
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    public ResponseEntity<SuggestionAcquisitionResponseDTO> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusSuggestionRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(
                suggestionService.changeStatus(id, dto.freshStatus(), authentication));
    }

    // ── GET /api/v1/sugerencias-adquisicion/mas-pedidos ──
    // Gestión por demanda (GERENTE/ADMIN): PENDIENTE agrupadas por ISBN.
    // Importante: va ANTES de que alguien agregue un @GetMapping("/{id}")
    // para que "mas-pedidos" no se confunda con un id.
    /**
     * Procesa most pedidos y devuelve el resultado calculado por el backend.
     *
     * @param pageable configuracion de pagina, tamano y orden usada para limitar la consulta
     * @return respuesta HTTP con el estado y el cuerpo definidos por la operacion
     */
    @GetMapping("/mas-pedidos")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SuggestionGroupedDTO>> mostPedidos(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(suggestionService.getMostPedidos(pageable));
    }

    // ── POST /api/v1/sugerencias-adquisicion/confirmar-adquisicion?isbn= ──
    // Marca adquiridas todas las PENDIENTE de ese ISBN (salen del agrupado).
    /**
     * Procesa confirm acquisition y devuelve el resultado calculado por el backend.
     *
     * @param isbn valor de entrada isbn usado por la operacion para completar su regla de negocio
     * @param authentication identidad autenticada usada para aplicar permisos y registrar autoria de la accion
     * @return objeto con el resultado de la operacion y los datos relevantes para el cliente
     */
    @PostMapping("/confirmar-adquisicion")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> confirmAcquisition(
            @RequestParam String isbn,
            Authentication authentication) {
        Long revisorId = authentication == null ? null
                : suggestionService.resolveIdByEmailPublic(authentication.getName());
        int confirmadas = suggestionService.confirmAcquisition(isbn, revisorId);
        return ResponseEntity.ok(java.util.Map.of("isbn", isbn, "confirmadas", confirmadas));
    }

    // ── GET /api/v1/sugerencias-adquisicion/reporte-pdf ──
    @GetMapping("/reporte-pdf")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Handles report pdf.
     *
     * @return response entity<byte[]> with the resulting state after the operation
     */
    public ResponseEntity<byte[]> reportPdf() {
        byte[] pdf = reportPdfService.generateReportSuggestionsMostPedidas(
                suggestionService.getMostPedidosList());
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reporte-sugerencias-mas-pedidas.pdf")
                .body(pdf);
    }
}
