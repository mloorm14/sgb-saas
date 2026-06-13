package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LibroServiceTest {

    @Mock LibroRepository libroRepo;
    @Mock EditorialRepository editorialRepo;
    @Mock IdiomaRepository idiomaRepo;
    @Mock EstadoLibroRepository estadoRepo;

    @InjectMocks LibroService libroService;

    // ── Test 1: crear libro exitosamente ──────────────────
    @Test
    void crearLibro_cuandoIsbnNuevo_retornaDTO() {
        given(libroRepo.existsByIsbn("978-1234567890")).willReturn(false);
        given(libroRepo.save(any())).willReturn(libroConId());

        LibroResponseDTO resultado = libroService.crear(requestDTO());

        assertThat(resultado.isbn()).isEqualTo("978-1234567890");
        assertThat(resultado.titulo()).isEqualTo("Clean Code");
    }

    // ── Test 2: ISBN duplicado lanza excepcion ────────────
    @Test
    void crearLibro_cuandoIsbnDuplicado_lanzaExcepcion() {
        given(libroRepo.existsByIsbn("978-1234567890")).willReturn(true);

        assertThatThrownBy(() -> libroService.crear(requestDTO()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN ya registrado");
    }

    // ── Test 3: buscar libro que no existe lanza 404 ──────
    @Test
    void buscarPorId_cuandoNoExiste_lanzaEntityNotFound() {
        given(libroRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> libroService.buscarPorId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Test 4: soft delete desactiva el libro ────────────
    @Test
    void eliminar_cuandoExiste_desactivaLibro() {
        Libro libro = libroConId();
        given(libroRepo.findById(1L)).willReturn(Optional.of(libro));

        libroService.eliminar(1L);

        assertThat(libro.getActivo()).isFalse();
        verify(libroRepo).save(libro);
    }

    // ── Test 5: listar devuelve pagina de resultados ──────
    @Test
    void listar_retornaPaginaDeLibros() {
        Page<Libro> pagina = new PageImpl<>(List.of(libroConId()));
        given(libroRepo.findByActivoTrue(any())).willReturn(pagina);

        Page<LibroResponseDTO> resultado = libroService.listar(Pageable.unpaged());

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).titulo()).isEqualTo("Clean Code");
    }

    // ── Helpers ───────────────────────────────────────────
    private Libro libroConId() {
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo("Clean Code");
        libro.setIsbn("978-1234567890");
        libro.setActivo(true);
        return libro;
    }

    private LibroRequestDTO requestDTO() {
        return new LibroRequestDTO(
                "Clean Code",
                "978-1234567890",
                "Robert C. Martin",
                2008,
                null,
                null,
                null
        );
    }
}