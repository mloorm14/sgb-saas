package com.uteq.backend.controller;

import com.uteq.backend.dto.AutorRequestDTO;
import com.uteq.backend.dto.AutorResponseDTO;
import com.uteq.backend.entity.Autor;
import com.uteq.backend.repository.AutorRepository;
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
class AutorControllerTest {

    @Test
    void listar_devuelveTodos() {
        AutorRepository repo = mock(AutorRepository.class);
        Autor a1 = new Autor(); a1.setId(1L); a1.setNombre("García Márquez");
        Autor a2 = new Autor(); a2.setId(2L); a2.setNombre("Allende");
        given(repo.findAll()).willReturn(List.of(a1, a2));

        List<AutorResponseDTO> resultado = new AutorController(repo).listar().getBody();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0)).isEqualTo(new AutorResponseDTO(1L, "García Márquez"));
        assertThat(resultado.get(1).nombre()).isEqualTo("Allende");
    }

    @Test
    void buscar_devuelveCoincidencias() {
        AutorRepository repo = mock(AutorRepository.class);
        Autor a = new Autor(); a.setId(1L); a.setNombre("García Márquez");
        given(repo.findTop5ByNombreContainingIgnoreCase("García")).willReturn(List.of(a));

        List<AutorResponseDTO> resultado = new AutorController(repo).buscar("García").getBody();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("García Márquez");
    }

    @Test
    void crear_guardaYDevuelveCreated() {
        AutorRepository repo = mock(AutorRepository.class);

        Autor guardado = new Autor(); guardado.setId(10L); guardado.setNombre("Nuevo Autor");
        given(repo.save(org.mockito.ArgumentMatchers.any(Autor.class))).willReturn(guardado);

        ResponseEntity<AutorResponseDTO> response = new AutorController(repo)
                .crear(new AutorRequestDTO("Nuevo Autor"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo(10L);
        assertThat(response.getBody().nombre()).isEqualTo("Nuevo Autor");
        verify(repo).save(org.mockito.ArgumentMatchers.any(Autor.class));
    }

    @Test
    void listar_catalogoVacio_devuelveListaVacia() {
        AutorRepository repo = mock(AutorRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<AutorResponseDTO> resultado = new AutorController(repo).listar().getBody();

        assertThat(resultado).isEmpty();
    }
}
