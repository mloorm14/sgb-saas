package com.uteq.backend.controller;

import com.uteq.backend.dto.LanguageRequestDTO;
import com.uteq.backend.dto.LanguageResponseDTO;
import com.uteq.backend.entity.Language;
import com.uteq.backend.repository.LanguageRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

// Catálogo idiomas (FIX 3): los <select> del formulario de libros
// necesitan listar idiomas. Mismo patrón que CategoriaController/
// AutorController (GET sin @PreAuthorize, array plano sin paginación).
@RestController
@RequestMapping("/api/v1/idiomas")
public class LanguageController {

    private final LanguageRepository languageRepository;

    public LanguageController(LanguageRepository languageRepository) {
        this.languageRepository = languageRepository;
    }

    @GetMapping
    /**
     * Lists language.
     *
     * @return response entity<list<idioma response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<LanguageResponseDTO>> list() {
        List<LanguageResponseDTO> languages = languageRepository.findAll().stream()
                .map(i -> new LanguageResponseDTO(i.getId(), i.getName()))
                .toList();
        return ResponseEntity.ok(languages);
    }

    @GetMapping("/buscar")
    /**
     * Searches Response Entity&lt;List<Idioma Response DTO>>.
     *
     * @param q text value used to scope this Response Entity&lt;List<Idioma Response DTO>>
     * @return Response Entity&lt;List<Idioma Response DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<LanguageResponseDTO>> search(@RequestParam String q) {
        return ResponseEntity.ok(
                languageRepository.findTop5ByNameContainingIgnoreCase(q).stream()
                        .map(i -> new LanguageResponseDTO(i.getId(), i.getName()))
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Creates Response Entity&lt;Idioma Response DTO>.
     *
     * @param dto language Request data transfer object used to scope this Response Entity&lt;Idioma Response DTO>
     * @return Response Entity&lt;Idioma Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<LanguageResponseDTO> create(@Valid @RequestBody LanguageRequestDTO dto) {
        if (languageRepository.existsByNameIgnoreCase(dto.name())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Language ent = new Language();
        ent.setName(dto.name());
        String base = dto.name().toLowerCase().replaceAll("[^a-z]", "");
        if (base.isBlank()) base = "xx";
        String code = base.substring(0, Math.min(3, base.length()));
        int suffix = 1;
        while (languageRepository.existsByCodeIgnoreCase(code)) {
            String suf = String.valueOf(suffix++);
            code = base.substring(0, Math.min(2, base.length())) + suf;
            if (code.length() > 5) code = code.substring(0, 5);
        }
        ent.setCode(code);
        Language guardado = languageRepository.save(ent);
        return ResponseEntity.created(URI.create("/api/v1/idiomas/" + guardado.getId()))
                .body(new LanguageResponseDTO(guardado.getId(), guardado.getName()));
    }
}