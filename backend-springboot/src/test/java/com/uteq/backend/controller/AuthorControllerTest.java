package com.uteq.backend.controller;

import com.uteq.backend.dto.AuthorRequestDTO;
import com.uteq.backend.dto.AuthorResponseDTO;
import com.uteq.backend.entity.Author;
import com.uteq.backend.repository.AuthorRepository;
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
class AuthorControllerTest {

    @Test
    void list_devuelveTodos() {
        AuthorRepository repo = mock(AuthorRepository.class);
        Author a1 = new Author(); a1.setId(1L); a1.setName("García Márquez");
        Author a2 = new Author(); a2.setId(2L); a2.setName("Allende");
        given(repo.findAll()).willReturn(List.of(a1, a2));

        List<AuthorResponseDTO> result = new AuthorController(repo).list().getBody();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new AuthorResponseDTO(1L, "García Márquez"));
        assertThat(result.get(1).name()).isEqualTo("Allende");
    }

    @Test
    void search_devuelveCoincidencias() {
        AuthorRepository repo = mock(AuthorRepository.class);
        Author a = new Author(); a.setId(1L); a.setName("García Márquez");
        given(repo.findTop5ByNameContainingIgnoreCase("García")).willReturn(List.of(a));

        List<AuthorResponseDTO> result = new AuthorController(repo).search("García").getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("García Márquez");
    }

    @Test
    void create_guardaYDevuelveCreated() {
        AuthorRepository repo = mock(AuthorRepository.class);

        Author guardado = new Author(); guardado.setId(10L); guardado.setName("Nuevo Autor");
        given(repo.save(org.mockito.ArgumentMatchers.any(Author.class))).willReturn(guardado);

        ResponseEntity<AuthorResponseDTO> response = new AuthorController(repo)
                .create(new AuthorRequestDTO("Nuevo Autor"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo(10L);
        assertThat(response.getBody().name()).isEqualTo("Nuevo Autor");
        verify(repo).save(org.mockito.ArgumentMatchers.any(Author.class));
    }

    @Test
    void list_catalogoVacio_devuelveListaVacia() {
        AuthorRepository repo = mock(AuthorRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<AuthorResponseDTO> result = new AuthorController(repo).list().getBody();

        assertThat(result).isEmpty();
    }
}
