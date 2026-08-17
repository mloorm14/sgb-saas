package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.AutorRepository;
import com.uteq.backend.repository.CategoriaRepository;
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
class LibroServiceTest {

    @Mock LibroRepository libroRepo;
    @Mock EditorialRepository editorialRepo;
    @Mock IdiomaRepository idiomaRepo;
    @Mock EstadoLibroRepository estadoRepo;
    // Módulo 9.1/3 (rama E): repos nuevos que ahora recibe el constructor
    // de LibroService. No se stubean en los tests preexistentes (1-5)
    // porque esos flujos nunca resuelven categoriaIds/autorIds no-nulos --
    // Mockito los inyecta igual por tipo vía @InjectMocks, pero
    // permanecen "unused" (sin given(...)) en esos casos, que es lo
    // esperado.
    @Mock CategoriaRepository categoriaRepo;
    @Mock AutorRepository autorRepo;
    // Módulo portada binaria: para leer max_tamano_portada_mb (límite de
    // tamaño de portada en configuracion_sistema). Solo se stubea en los
    // tests de actualizarPortada_*.
    @Mock ConfiguracionSistemaService configuracionSistemaService;

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

    // ── Test 4: soft delete mueve el libro a estado DADO_DE_BAJA ──
    @Test
    void eliminar_cuandoExiste_loMarcaDadoDeBaja() {
        Libro libro = libroConId();
        EstadoLibro dadoDeBaja = estadoConNombre("DADO_DE_BAJA");
        given(libroRepo.findById(1L)).willReturn(Optional.of(libro));
        given(estadoRepo.findByNombre("DADO_DE_BAJA")).willReturn(Optional.of(dadoDeBaja));

        libroService.eliminar(1L);

        assertThat(libro.getEstado()).isEqualTo(dadoDeBaja);
        verify(libroRepo).save(libro);
    }

    // ── Test 5: listar devuelve pagina de resultados ──────
    @Test
    void listar_retornaPaginaDeLibros() {
        Page<Libro> pagina = new PageImpl<>(List.of(libroConId()));
        given(libroRepo.findByEstado_Nombre(anyString(), any())).willReturn(pagina);

        Page<LibroResponseDTO> resultado = libroService.listar(Pageable.unpaged());

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).titulo()).isEqualTo("Clean Code");
    }

    // ── Test 6 (Módulo 3): sugerir con texto parcial retorna
    // coincidencias ordenadas por relevancia ──────────────
    @Test
    void sugerir_conTextoParcial_retornaCoincidenciasOrdenadas() {
        EstadoLibro activo = estadoConNombre("ACTIVO");
        Libro coincidencia = libroConId();
        coincidencia.setStockDisponible((short) 2);
        given(estadoRepo.findByNombre("ACTIVO")).willReturn(Optional.of(activo));
        given(libroRepo.sugerirPorTitulo("clean", activo.getId()))
                .willReturn(List.of(coincidencia));

        List<LibroSugerenciaDTO> resultado = libroService.sugerir("clean");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).titulo()).isEqualTo("Clean Code");
        assertThat(resultado.get(0).disponible()).isTrue();
    }

    // ── Test 7 (Módulo 3): sin coincidencias retorna lista vacía ──
    @Test
    void sugerir_sinCoincidencias_retornaListaVacia() {
        EstadoLibro activo = estadoConNombre("ACTIVO");
        given(estadoRepo.findByNombre("ACTIVO")).willReturn(Optional.of(activo));
        given(libroRepo.sugerirPorTitulo(anyString(), anyInt())).willReturn(List.of());

        List<LibroSugerenciaDTO> resultado = libroService.sugerir("xyz-inexistente");

        assertThat(resultado).isEmpty();
    }

    // ── Test 8 (portada binaria): archivo válido guarda binario y ──
    // limpia portadaUrl ────────────────────────────────────
    @Test
    void actualizarPortada_conArchivoValido_guardaBinarioYLimpiarPortadaUrl() {
        Libro libro = libroConId();
        libro.setPortadaUrl("https://host-externo/portada.png");
        given(libroRepo.findById(1L)).willReturn(Optional.of(libro));
        given(configuracionSistemaService.obtenerValorEntero("max_tamano_portada_mb")).willReturn(2);
        given(libroRepo.save(any())).willReturn(libro);
        byte[] binario = new byte[1024];
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "portada.png", "image/png", binario);

        LibroResponseDTO resultado = libroService.actualizarPortada(1L, archivo);

        assertThat(resultado.tienePortada()).isTrue();
        assertThat(resultado.portadaNombre()).isEqualTo("portada.png");
        assertThat(resultado.portadaTipo()).isEqualTo("image/png");
        // El binario queda en la entidad (y de ahi viaja a la BD) exacto.
        assertThat(libro.getPortadaImagen()).isEqualTo(binario);
        assertThat(libro.getPortadaTamanio()).isEqualTo(binario.length);
        // La URL externa se descarta: la fuente vigente es el binario.
        assertThat(libro.getPortadaUrl()).isNull();
        verify(libroRepo).save(libro);
    }

    // ── Test 9: tipo no permitido -> 400 (IllegalArgumentException) ──
    @Test
    void actualizarPortada_conTipoNoPermitido_lanzaExcepcion() {
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConId()));
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "portada.gif", "image/gif", new byte[10]);

        assertThatThrownBy(() -> libroService.actualizarPortada(1L, archivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de imagen no permitido");
        verify(libroRepo, never()).save(any());
    }

    // ── Test 10: tamaño excedido -> 400 (IllegalArgumentException) ──
    // MockMultipartFile no pasa por el límite de servlet (spring.servlet
    // .multipart), así que el corte lo hace la regla de negocio con el
    // límite leído de configuracion_sistema.
    @Test
    void actualizarPortada_conTamanoExcedido_lanzaExcepcion() {
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConId()));
        given(configuracionSistemaService.obtenerValorEntero("max_tamano_portada_mb")).willReturn(2);
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "grande.png", "image/png", new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> libroService.actualizarPortada(1L, archivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede el tamaño máximo");
        verify(libroRepo, never()).save(any());
    }

    // ── Test 11: subir portada de libro inexistente -> 404 ──
    @Test
    void actualizarPortada_cuandoLibroNoExiste_lanzaEntityNotFound() {
        given(libroRepo.findById(999L)).willReturn(Optional.empty());
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "portada.png", "image/png", new byte[10]);

        assertThatThrownBy(() -> libroService.actualizarPortada(999L, archivo))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── Test 12: obtenerPortada de libro sin portada -> 404 ──
    @Test
    void obtenerPortada_cuandoNoTienePortada_lanzaEntityNotFound() {
        given(libroRepo.findById(1L)).willReturn(Optional.of(libroConId()));

        assertThatThrownBy(() -> libroService.obtenerPortada(1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("no tiene portada");
    }

    // ── Test 13: obtenerPortada con portada devuelve bytes y tipo ──
    @Test
    void obtenerPortada_cuandoTienePortada_retornaBytesYTipo() {
        Libro libro = libroConId();
        libro.setPortadaImagen(new byte[]{1, 2, 3});
        libro.setPortadaTipo("image/png");
        given(libroRepo.findById(1L)).willReturn(Optional.of(libro));

        PortadaImagenDTO portada = libroService.obtenerPortada(1L);

        assertThat(portada.bytes()).containsExactly(1, 2, 3);
        assertThat(portada.contentType()).isEqualTo("image/png");
    }

    // ── Test 14 (FIX inventario): crear persiste ubicacionFisica y el ──
    // DTO de respuesta la devuelve (LibroRequestDTO.ubicacionFisica) ──
    @Test
    void crearLibro_persisteUbicacionFisicaYLaDevuelveEnDTO() {
        given(libroRepo.existsByIsbn("978-1234567890")).willReturn(false);
        given(libroRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        LibroResponseDTO resultado = libroService.crear(requestDTO());

        assertThat(resultado.ubicacionFisica()).isEqualTo("Estante A-12");
        verify(libroRepo).save(argThat(l -> "Estante A-12".equals(l.getUbicacionFisica())));
    }

    // ── Test 15 (FIX inventario): actualizar mapea la ubicación al ──
    // libro existente y el DTO la refleja ──────────────────────
    @Test
    void actualizarLibro_mapeaUbicacionFisicaYLaDevuelveEnDTO() {
        Libro libro = libroConId();
        libro.setUbicacionFisica("Estante viejo");
        given(libroRepo.findById(1L)).willReturn(Optional.of(libro));
        given(libroRepo.existsByIsbnAndIdNot("978-1234567890", 1L)).willReturn(false);
        given(libroRepo.save(any())).willReturn(libro);

        LibroResponseDTO resultado = libroService.actualizar(1L, requestDTO());

        assertThat(resultado.ubicacionFisica()).isEqualTo("Estante A-12");
        assertThat(libro.getUbicacionFisica()).isEqualTo("Estante A-12");
    }

    // ── Helpers ───────────────────────────────────────────
    private Libro libroConId() {
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo("Clean Code");
        libro.setIsbn("978-1234567890");
        libro.setEstado(estadoConNombre("ACTIVO"));
        return libro;
    }

    private EstadoLibro estadoConNombre(String nombre) {
        EstadoLibro estado = new EstadoLibro();
        estado.setId(1);
        estado.setNombre(nombre);
        return estado;
    }

    private LibroRequestDTO requestDTO() {
        return new LibroRequestDTO(
                "Clean Code",
                "978-1234567890",
                2008,
                null,
                "Estante A-12",
                null,
                1,
                1,
                1,
                1,
                1,
                null,
                null
        );
    }
}