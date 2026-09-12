package com.uteq.backend.service;

import com.uteq.backend.dto.BookRequestDTO;
import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.entity.StatusBook;
import com.uteq.backend.entity.Book;
import com.uteq.backend.repository.AuthorRepository;
import com.uteq.backend.repository.CategoryRepository;
import com.uteq.backend.repository.PublisherRepository;
import com.uteq.backend.repository.StatusBookRepository;
import com.uteq.backend.repository.LanguageRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock BookRepository bookRepo;
    @Mock PublisherRepository publisherRepo;
    @Mock LanguageRepository languageRepo;
    @Mock StatusBookRepository statusRepo;
    @Mock CategoryRepository categoryRepo;
    @Mock AuthorRepository authorRepo;
    @Mock SupplierRepository supplierRepo;
    @Mock ConfigurationSystemService configurationSystemService;
    @Mock SuggestionAcquisitionService suggestionAcquisitionService;

    @InjectMocks BookService bookService;

    // ── Test 1: crear libro exitosamente ──────────────────
    @Test
    void createBook_cuandoIsbnFresh_retornaDTO() {
        given(bookRepo.existsByIsbn("9780132350884")).willReturn(false);
        given(bookRepo.save(any())).willReturn(bookWithId());

        BookResponseDTO result = bookService.create(requestDTO());

        assertThat(result.isbn()).isEqualTo("9780132350884");
        assertThat(result.title()).isEqualTo("Clean Code");
    }

    // ── Test 2: ISBN duplicado lanza excepcion ────────────
    @Test
    void createBook_cuandoIsbnDuplicate_lanzaException() {
        given(bookRepo.existsByIsbn("9780132350884")).willReturn(true);

        assertThatThrownBy(() -> bookService.create(requestDTO()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN ya registrado");
    }

    // ── Crear con ISBN pedido confirma las sugerencias pendientes ──
    @Test
    void createBook_withIsbnPedido_confirmaSuggestions() {
        given(bookRepo.existsByIsbn("9780132350884")).willReturn(false);
        given(bookRepo.save(any())).willReturn(bookWithId());

        bookService.create(requestDTO());

        verify(suggestionAcquisitionService).confirmAcquisition("9780132350884", null);
    }

    // ── Test 3: buscar libro que no existe lanza 404 ──────
    @Test
    void searchById_cuandoNotExiste_lanzaEntityNotFound() {
        given(bookRepo.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.searchById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── Test 4: soft delete mueve el libro a estado DADO_DE_BAJA ──
    @Test
    void delete_cuandoExiste_loMarkDadoRemoval() {
        Book book = bookWithId();
        StatusBook dadoRemoval = statusWithName("DADO_DE_BAJA");
        given(bookRepo.findById(1L)).willReturn(Optional.of(book));
        given(statusRepo.findByName("DADO_DE_BAJA")).willReturn(Optional.of(dadoRemoval));

        bookService.delete(1L);

        assertThat(book.getStatus()).isEqualTo(dadoRemoval);
        verify(bookRepo).save(book);
    }

    // ── Test 5: listar devuelve pagina de resultados ──────
    @Test
    void list_retornaPageBooks() {
        Page<Book> page = new PageImpl<>(List.of(bookWithId()));
        given(bookRepo.findByStatus_Name(anyString(), any())).willReturn(page);

        Page<BookResponseDTO> result = bookService.list(Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Clean Code");
    }

    // ── Test 6 (Módulo 3): sugerir con texto parcial retorna
    // coincidencias ordenadas por relevancia ──────────────
    @Test
    void sugerir_withTextParcial_retornaCoincidenciasOrdenadas() {
        StatusBook active = statusWithName("ACTIVO");
        Book coincidencia = bookWithId();
        coincidencia.setStockAvailable((short) 2);
        given(statusRepo.findByName("ACTIVO")).willReturn(Optional.of(active));
        given(bookRepo.sugerirByTitle("clean", active.getId()))
                .willReturn(List.of(coincidencia));

        List<BookSuggestionDTO> result = bookService.sugerir("clean");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Clean Code");
        assertThat(result.get(0).available()).isTrue();
    }

    // ── Test 7 (Módulo 3): sin coincidencias retorna lista vacía ──
    @Test
    void sugerir_withoutCoincidencias_retornaListaVacia() {
        StatusBook active = statusWithName("ACTIVO");
        given(statusRepo.findByName("ACTIVO")).willReturn(Optional.of(active));
        given(bookRepo.sugerirByTitle(anyString(), anyInt())).willReturn(List.of());

        List<BookSuggestionDTO> result = bookService.sugerir("xyz-inexistente");

        assertThat(result).isEmpty();
    }

    // ── Test 8 (portada binaria): archivo válido guarda binario y ──
    // limpia portadaUrl ────────────────────────────────────
    @Test
    void updateCover_withFileValid_guardaBinarioYLimpiarCoverUrl() {
        Book book = bookWithId();
        book.setCoverUrl("https://host-externo/portada.png");
        given(bookRepo.findById(1L)).willReturn(Optional.of(book));
        given(configurationSystemService.getValueEntero("max_tamano_portada_mb")).willReturn(2);
        given(bookRepo.save(any())).willReturn(book);
        byte[] binario = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "portada.png", "image/png", binario);

        BookResponseDTO result = bookService.updateCover(1L, file);

        assertThat(result.tieneCover()).isTrue();
        assertThat(result.coverName()).isEqualTo("portada.png");
        assertThat(result.coverType()).isEqualTo("image/png");
        // El binario queda en la entidad (y de ahi viaja a la BD) exacto.
        assertThat(book.getCoverImage()).isEqualTo(binario);
        assertThat(book.getCoverTamanio()).isEqualTo(binario.length);
        // La URL externa se descarta: la fuente vigente es el binario.
        assertThat(book.getCoverUrl()).isNull();
        verify(bookRepo).save(book);
    }

    // ── Test 9: tipo no permitido -> 400 (IllegalArgumentException) ──
    @Test
    void updateCover_withTypeNotAllowed_lanzaException() {
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithId()));
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "portada.gif", "image/gif", new byte[10]);

        assertThatThrownBy(() -> bookService.updateCover(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de imagen no permitido");
        verify(bookRepo, never()).save(any());
    }

    // ── Test 10: tamaño excedido -> 400 (IllegalArgumentException) ──
    // MockMultipartFile no pasa por el límite de servlet (spring.servlet
    // .multipart), así que el corte lo hace la regla de negocio con el
    // límite leído de configuracion_sistema.
    @Test
    void updateCover_withSizeExceeded_lanzaException() {
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithId()));
        given(configurationSystemService.getValueEntero("max_tamano_portada_mb")).willReturn(2);
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "grande.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> bookService.updateCover(1L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede el tamaño máximo");
        verify(bookRepo, never()).save(any());
    }

    // ── Test 11: subir portada de libro inexistente -> 404 ──
    @Test
    void updateCover_cuandoBookNotExiste_lanzaEntityNotFound() {
        given(bookRepo.findById(999L)).willReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "archivo", "portada.png", "image/png", new byte[10]);

        assertThatThrownBy(() -> bookService.updateCover(999L, file))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 12: obtenerPortada de libro sin portada -> 404 ──
    @Test
    void getCover_cuandoNotTieneCover_lanzaEntityNotFound() {
        given(bookRepo.findById(1L)).willReturn(Optional.of(bookWithId()));

        assertThatThrownBy(() -> bookService.getCover(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("no tiene portada");
    }

    // ── Test 13: obtenerPortada con portada devuelve bytes y tipo ──
    @Test
    void getCover_cuandoTieneCover_retornaBytesYType() {
        Book book = bookWithId();
        book.setCoverImage(new byte[]{1, 2, 3});
        book.setCoverType("image/png");
        given(bookRepo.findById(1L)).willReturn(Optional.of(book));

        CoverImageDTO cover = bookService.getCover(1L);

        assertThat(cover.bytes()).containsExactly(1, 2, 3);
        assertThat(cover.contentType()).isEqualTo("image/png");
    }

    // ── Test 14 (FIX inventario): crear persiste ubicacionFisica y el ──
    // DTO de respuesta la devuelve (LibroRequestDTO.ubicacionFisica) ──
    @Test
    void createBook_persisteLocationPhysicalYDevuelveDTO() {
        given(bookRepo.existsByIsbn("9780132350884")).willReturn(false);
        given(bookRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        BookResponseDTO result = bookService.create(requestDTO());

        assertThat(result.locationPhysical()).isEqualTo("Estante A-12");
        verify(bookRepo).save(argThat(l -> "Estante A-12".equals(l.getLocationPhysical())));
    }

    // ── Test 15 (FIX inventario): actualizar mapea la ubicación al ──
    // libro existente y el DTO la refleja ──────────────────────
    @Test
    void updateBook_mapeaLocationPhysicalYDevuelveDTO() {
        Book book = bookWithId();
        book.setLocationPhysical("Estante viejo");
        given(bookRepo.findById(1L)).willReturn(Optional.of(book));
        given(bookRepo.existsByIsbnAndIdNot("9780132350884", 1L)).willReturn(false);
        given(bookRepo.save(any())).willReturn(book);

        BookResponseDTO result = bookService.update(1L, requestDTO());

        assertThat(result.locationPhysical()).isEqualTo("Estante A-12");
        assertThat(book.getLocationPhysical()).isEqualTo("Estante A-12");
    }

    // ── Helpers ───────────────────────────────────────────
    private Book bookWithId() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("Clean Code");
        book.setIsbn("9780132350884");
        book.setStatus(statusWithName("ACTIVO"));
        return book;
    }

    private StatusBook statusWithName(String name) {
        StatusBook status = new StatusBook();
        status.setId(1);
        status.setName(name);
        return status;
    }

    private BookRequestDTO requestDTO() {
        return new BookRequestDTO(
                "Clean Code",
                "9780132350884",
                2008,
                null,
                null,
                null,
                "Estante A-12",
                null,
                1,
                1,
                1,
                1,
                1,
                null,
                null,
                null
        );
    }
}
