package com.uteq.backend.controller;

import com.uteq.backend.dto.PublisherResponseDTO;
import com.uteq.backend.dto.StatusBookResponseDTO;
import com.uteq.backend.dto.LanguageResponseDTO;
import com.uteq.backend.entity.Publisher;
import com.uteq.backend.entity.StatusBook;
import com.uteq.backend.entity.Language;
import com.uteq.backend.repository.PublisherRepository;
import com.uteq.backend.repository.StatusBookRepository;
import com.uteq.backend.repository.LanguageRepository;
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
class CatalogosBookControllerTest {

    @Test
    void listPublishers_mapeaIdYName() {
        PublisherRepository repo = mock(PublisherRepository.class);
        Publisher ed1 = new Publisher();
        ed1.setId(1);
        ed1.setName("Editorial XYZ");
        Publisher ed2 = new Publisher();
        ed2.setId(2);
        ed2.setName("Pearson");
        given(repo.findAll()).willReturn(List.of(ed1, ed2));

        List<PublisherResponseDTO> result = new PublisherController(repo).list().getBody();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new PublisherResponseDTO(1, "Editorial XYZ"));
        assertThat(result.get(1).name()).isEqualTo("Pearson");
    }

    @Test
    void listLanguages_mapeaIdYName() {
        LanguageRepository repo = mock(LanguageRepository.class);
        Language es = new Language();
        es.setId(1);
        es.setName("Español");
        given(repo.findAll()).willReturn(List.of(es));

        List<LanguageResponseDTO> result = new LanguageController(repo).list().getBody();

        assertThat(result).containsExactly(new LanguageResponseDTO(1, "Español"));
    }

    @Test
    void listStatusesBook_mapeaIdYName() {
        StatusBookRepository repo = mock(StatusBookRepository.class);
        StatusBook active = new StatusBook();
        active.setId(1);
        active.setName("Activo");
        given(repo.findAll()).willReturn(List.of(active));

        List<StatusBookResponseDTO> result = new StatusBookController(repo).list().getBody();

        assertThat(result).containsExactly(new StatusBookResponseDTO(1, "Activo"));
    }

    @Test
    void listPublishers_catalogoVacio_devuelveListaVacia() {
        PublisherRepository repo = mock(PublisherRepository.class);
        given(repo.findAll()).willReturn(List.of());

        List<PublisherResponseDTO> result = new PublisherController(repo).list().getBody();

        assertThat(result).isEmpty();
    }
}