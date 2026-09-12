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
     * Creates Response Entity&lt;Sugerencia Adquisicion Response DTO>.
     *
     * @param dto suggestion Adquisicion Request data transfer object used to scope this Response Entity&lt;Sugerencia Adquisicion Response DTO>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Sugerencia Adquisicion Response DTO>
     * @return Response Entity&lt;Sugerencia Adquisicion Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<SuggestionAcquisitionResponseDTO> create(
            @Valid @RequestBody SuggestionAcquisitionRequestDTO dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suggestionService.create(dto, authentication));
    }

    // ── GET /api/v1/sugerencias-adquisicion/mias ──────────
    @GetMapping("/mias")
    @PreAuthorize("hasRole('LECTOR')")
    public ResponseEntity<Page<SuggestionAcquisitionResponseDTO>> listOwns(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "created") Pageable pageable) {
        return ResponseEntity.ok(suggestionService.listOwns(authentication, pageable));
    }

    // ── GET /api/v1/sugerencias-adquisicion?estado=PENDIENTE ──
    @GetMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SuggestionAcquisitionResponseDTO>> listTodas(
            @RequestParam(name = "estado", required = false) String status,
            @PageableDefault(size = 10, sort = "created") Pageable pageable) {
        return ResponseEntity.ok(suggestionService.listTodas(status, pageable));
    }

    // ── PATCH /api/v1/sugerencias-adquisicion/{id}/estado ─
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Changes Response Entity&lt;Sugerencia Adquisicion Response DTO>.
     *
     * @param id numeric identifier used to scope this Response Entity&lt;Sugerencia Adquisicion Response DTO>
     * @param dto Cambio status suggestion Request data transfer object used to scope this Response Entity&lt;Sugerencia Adquisicion Response DTO>
     * @param authentication authentication of the caller used to scope this Response Entity&lt;Sugerencia Adquisicion Response DTO>
     * @return Response Entity&lt;Sugerencia Adquisicion Response DTO> reflecting the state after the operation
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
    @GetMapping("/mas-pedidos")
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    public ResponseEntity<Page<SuggestionGroupedDTO>> mostPedidos(
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(suggestionService.getMostPedidos(pageable));
    }

    // ── POST /api/v1/sugerencias-adquisicion/confirmar-adquisicion?isbn= ──
    // Marca adquiridas todas las PENDIENTE de ese ISBN (salen del agrupado).
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
