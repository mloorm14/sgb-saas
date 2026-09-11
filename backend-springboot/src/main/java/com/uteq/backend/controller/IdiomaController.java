package com.uteq.backend.controller;

import com.uteq.backend.dto.IdiomaRequestDTO;
import com.uteq.backend.dto.IdiomaResponseDTO;
import com.uteq.backend.entity.Idioma;
import com.uteq.backend.repository.IdiomaRepository;
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
public class IdiomaController {

    private final IdiomaRepository idiomaRepository;

    public IdiomaController(IdiomaRepository idiomaRepository) {
        this.idiomaRepository = idiomaRepository;
    }

    @GetMapping
    /**
     * Executes the listar operation.
     * @return operation result
     */
    public ResponseEntity<List<IdiomaResponseDTO>> listar() {
        List<IdiomaResponseDTO> idiomas = idiomaRepository.findAll().stream()
                .map(i -> new IdiomaResponseDTO(i.getId(), i.getNombre()))
                .toList();
        return ResponseEntity.ok(idiomas);
    }

    @GetMapping("/buscar")
    /**
     * Executes the buscar operation.
     * @param q value required by the operation
     * @return operation result
     */
    public ResponseEntity<List<IdiomaResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(
                idiomaRepository.findTop5ByNombreContainingIgnoreCase(q).stream()
                        .map(i -> new IdiomaResponseDTO(i.getId(), i.getNombre()))
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Executes the crear operation.
     * @param dto value required by the operation
     * @return operation result
     */
    public ResponseEntity<IdiomaResponseDTO> crear(@Valid @RequestBody IdiomaRequestDTO dto) {
        if (idiomaRepository.existsByNombreIgnoreCase(dto.nombre())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Idioma ent = new Idioma();
        ent.setNombre(dto.nombre());
        String base = dto.nombre().toLowerCase().replaceAll("[^a-z]", "");
        if (base.isBlank()) base = "xx";
        String codigo = base.substring(0, Math.min(3, base.length()));
        int suffix = 1;
        while (idiomaRepository.existsByCodigoIgnoreCase(codigo)) {
            String suf = String.valueOf(suffix++);
            codigo = base.substring(0, Math.min(2, base.length())) + suf;
            if (codigo.length() > 5) codigo = codigo.substring(0, 5);
        }
        ent.setCodigo(codigo);
        Idioma guardado = idiomaRepository.save(ent);
        return ResponseEntity.created(URI.create("/api/v1/idiomas/" + guardado.getId()))
                .body(new IdiomaResponseDTO(guardado.getId(), guardado.getNombre()));
    }
}