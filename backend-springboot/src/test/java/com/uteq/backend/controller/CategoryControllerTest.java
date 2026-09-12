package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoryRequestDTO;
import com.uteq.backend.dto.CategoryResponseDTO;
import com.uteq.backend.entity.Category;
import com.uteq.backend.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Test
    void list_devuelveTodas() {
        CategoryRepository repo = mock(CategoryRepository.class);
        Category c1 = new Category(); c1.setId(1); c1.setName("Ficción");
        Category c2 = new Category(); c2.setId(2); c2.setName("Historia");
        given(repo.findAll()).willReturn(List.of(c1, c2));

        List<CategoryResponseDTO> result = new CategoryController(repo).list().getBody();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new CategoryResponseDTO(1, "Ficción"));
        assertThat(result.get(1).name()).isEqualTo("Historia");
    }

    @Test
    void search_devuelveCoincidencias() {
        CategoryRepository repo = mock(CategoryRepository.class);
        Category c = new Category(); c.setId(1); c.setName("Ficción");
        given(repo.findTop5ByNameContainingIgnoreCase("Ficc")).willReturn(List.of(c));

        List<CategoryResponseDTO> result = new CategoryController(repo).search("Ficc").getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ficción");
    }

    @Test
    void create_guardaYDevuelveCreated() {
        CategoryRepository repo = mock(CategoryRepository.class);
        given(repo.existsByNameIgnoreCase("Nueva")).willReturn(false);

        Category guardada = new Category(); guardada.setId(10); guardada.setName("Nueva");
        given(repo.save(org.mockito.ArgumentMatchers.any(Category.class))).willReturn(guardada);

        ResponseEntity<CategoryResponseDTO> response = new CategoryController(repo)
                .create(new CategoryRequestDTO("Nueva"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo(10);
        assertThat(response.getBody().name()).isEqualTo("Nueva");
        verify(repo).save(org.mockito.ArgumentMatchers.any(Category.class));
    }

    @Test
    void create_duplicate_devuelve422() {
        CategoryRepository repo = mock(CategoryRepository.class);
        given(repo.existsByNameIgnoreCase("Ficción")).willReturn(true);

        ResponseEntity<CategoryResponseDTO> response = new CategoryController(repo)
                .create(new CategoryRequestDTO("Ficción"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void list_catalogoVacio_devuelveListaVacia() {
        CategoryRepository repo = mock(CategoryRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<CategoryResponseDTO> result = new CategoryController(repo).list().getBody();

        assertThat(result).isEmpty();
    }
}
