package com.uteq.backend.controller;

import com.uteq.backend.dto.CategoriaRequestDTO;
import com.uteq.backend.dto.CategoriaResponseDTO;
import com.uteq.backend.entity.Categoria;
import com.uteq.backend.repository.CategoriaRepository;
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
class CategoriaControllerTest {

    @Test
    void listar_devuelveTodas() {
        CategoriaRepository repo = mock(CategoriaRepository.class);
        Categoria c1 = new Categoria(); c1.setId(1); c1.setNombre("Ficción");
        Categoria c2 = new Categoria(); c2.setId(2); c2.setNombre("Historia");
        given(repo.findAll()).willReturn(List.of(c1, c2));

        List<CategoriaResponseDTO> resultado = new CategoriaController(repo).listar().getBody();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0)).isEqualTo(new CategoriaResponseDTO(1, "Ficción"));
        assertThat(resultado.get(1).nombre()).isEqualTo("Historia");
    }

    @Test
    void buscar_devuelveCoincidencias() {
        CategoriaRepository repo = mock(CategoriaRepository.class);
        Categoria c = new Categoria(); c.setId(1); c.setNombre("Ficción");
        given(repo.findTop5ByNombreContainingIgnoreCase("Ficc")).willReturn(List.of(c));

        List<CategoriaResponseDTO> resultado = new CategoriaController(repo).buscar("Ficc").getBody();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("Ficción");
    }

    @Test
    void crear_guardaYDevuelveCreated() {
        CategoriaRepository repo = mock(CategoriaRepository.class);
        given(repo.existsByNombreIgnoreCase("Nueva")).willReturn(false);

        Categoria guardada = new Categoria(); guardada.setId(10); guardada.setNombre("Nueva");
        given(repo.save(org.mockito.ArgumentMatchers.any(Categoria.class))).willReturn(guardada);

        ResponseEntity<CategoriaResponseDTO> response = new CategoriaController(repo)
                .crear(new CategoriaRequestDTO("Nueva"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isEqualTo(10);
        assertThat(response.getBody().nombre()).isEqualTo("Nueva");
        verify(repo).save(org.mockito.ArgumentMatchers.any(Categoria.class));
    }

    @Test
    void crear_duplicado_devuelve422() {
        CategoriaRepository repo = mock(CategoriaRepository.class);
        given(repo.existsByNombreIgnoreCase("Ficción")).willReturn(true);

        ResponseEntity<CategoriaResponseDTO> response = new CategoriaController(repo)
                .crear(new CategoriaRequestDTO("Ficción"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void listar_catalogoVacio_devuelveListaVacia() {
        CategoriaRepository repo = mock(CategoriaRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<CategoriaResponseDTO> resultado = new CategoriaController(repo).listar().getBody();

        assertThat(resultado).isEmpty();
    }
}
