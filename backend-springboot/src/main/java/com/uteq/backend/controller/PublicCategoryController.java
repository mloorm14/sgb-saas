package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoryResponseDTO;
import com.uteq.backend.repository.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/publico/categorias")
public class PublicCategoryController {

    private final CategoryRepository categoryRepository;

    public PublicCategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    /**
     * Lists publico category.
     *
     * @return response entity<list<categoria response dto>> with the resulting state after the operation
     */
    public ResponseEntity<List<CategoryResponseDTO>> list() {
        List<CategoryResponseDTO> categories = categoryRepository.findAll().stream()
                .map(c -> new CategoryResponseDTO(c.getId(), c.getName()))
                .toList();
        return ResponseEntity.ok(categories);
    }
}
