package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoryRequestDTO;
import com.uteq.backend.dto.CategoryResponseDTO;
import com.uteq.backend.entity.Category;
import com.uteq.backend.repository.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    /**
     * Lists category.
     *
     * @return response entity<list<categoria response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<CategoryResponseDTO>> list() {
        List<CategoryResponseDTO> categories = categoryRepository.findAll().stream()
                .map(c -> new CategoryResponseDTO(c.getId(), c.getName()))
                .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/buscar")
    /**
     * Searches Response Entity&lt;List<Categoria Response DTO>>.
     *
     * @param q text value used to scope this Response Entity&lt;List<Categoria Response DTO>>
     * @return Response Entity&lt;List<Categoria Response DTO>> reflecting the state after the operation
     */
    public ResponseEntity<List<CategoryResponseDTO>> search(@RequestParam String q) {
        return ResponseEntity.ok(
                categoryRepository.findTop5ByNameContainingIgnoreCase(q).stream()
                        .map(c -> new CategoryResponseDTO(c.getId(), c.getName()))
                        .toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE','ADMIN')")
    /**
     * Creates Response Entity&lt;Categoria Response DTO>.
     *
     * @param dto category Request data transfer object used to scope this Response Entity&lt;Categoria Response DTO>
     * @return Response Entity&lt;Categoria Response DTO> reflecting the state after the operation
     */
    public ResponseEntity<CategoryResponseDTO> create(@Valid @RequestBody CategoryRequestDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.name())) {
            return ResponseEntity.unprocessableEntity().build();
        }
        Category category = new Category();
        category.setName(dto.name());
        Category guardada = categoryRepository.save(category);
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + guardada.getId()))
                .body(new CategoryResponseDTO(guardada.getId(), guardada.getName()));
    }
}
