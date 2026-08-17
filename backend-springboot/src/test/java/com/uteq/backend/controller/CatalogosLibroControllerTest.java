package com.uteq.backend.controller;

import com.uteq.backend.dto.EditorialResponseDTO;
import com.uteq.backend.dto.EstadoLibroResponseDTO;
import com.uteq.backend.dto.IdiomaResponseDTO;
import com.uteq.backend.entity.Editorial;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.Idioma;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// FIX 3: catálogos expuestos para los <select> del formulario de libros.
// GETs triviales (findAll -> DTO id+nombre), mismo contrato que
// CategoriaController/AutorController. Test unitario directo: camino feliz
// (mapeo) y catálogo vacío; no hay rama de error de negocio en estos
// endpoints (un findAll() que falla es 500 genérico, no testeable acá).
@ExtendWith(MockitoExtension.class)
class CatalogosLibroControllerTest {

    @Test
    void listarEditoriales_mapeaIdYNombre() {
        EditorialRepository repo = mock(EditorialRepository.class);
        Editorial ed1 = new Editorial();
        ed1.setId(1);
        ed1.setNombre("Editorial XYZ");
        Editorial ed2 = new Editorial();
        ed2.setId(2);
        ed2.setNombre("Pearson");
        given(repo.findAll()).willReturn(List.of(ed1, ed2));

        List<EditorialResponseDTO> resultado = new EditorialController(repo).listar().getBody();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0)).isEqualTo(new EditorialResponseDTO(1, "Editorial XYZ"));
        assertThat(resultado.get(1).nombre()).isEqualTo("Pearson");
    }

    @Test
    void listarIdiomas_mapeaIdYNombre() {
        IdiomaRepository repo = mock(IdiomaRepository.class);
        Idioma es = new Idioma();
        es.setId(1);
        es.setNombre("Español");
        given(repo.findAll()).willReturn(List.of(es));

        List<IdiomaResponseDTO> resultado = new IdiomaController(repo).listar().getBody();

        assertThat(resultado).containsExactly(new IdiomaResponseDTO(1, "Español"));
    }

    @Test
    void listarEstadosLibro_mapeaIdYNombre() {
        EstadoLibroRepository repo = mock(EstadoLibroRepository.class);
        EstadoLibro activo = new EstadoLibro();
        activo.setId(1);
        activo.setNombre("Activo");
        given(repo.findAll()).willReturn(List.of(activo));

        List<EstadoLibroResponseDTO> resultado = new EstadoLibroController(repo).listar().getBody();

        assertThat(resultado).containsExactly(new EstadoLibroResponseDTO(1, "Activo"));
    }

    @Test
    void listarEditoriales_catalogoVacio_devuelveListaVacia() {
        EditorialRepository repo = mock(EditorialRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<EditorialResponseDTO> resultado = new EditorialController(repo).listar().getBody();

        assertThat(resultado).isEmpty();
    }
}