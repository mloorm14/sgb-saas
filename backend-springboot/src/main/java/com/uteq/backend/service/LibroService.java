package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.entity.Autor;
import com.uteq.backend.entity.Categoria;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.repository.AutorRepository;
import com.uteq.backend.repository.CategoriaRepository;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LibroService {

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_DADO_DE_BAJA = "DADO_DE_BAJA";

    private final LibroRepository libroRepo;
    private final EditorialRepository editorialRepo;
    private final IdiomaRepository idiomaRepo;
    private final EstadoLibroRepository estadoRepo;
    // Módulo 9.1/3: repos nuevos de la rama E, mismo criterio que
    // editorialRepo/idiomaRepo/estadoRepo -- inyectados directo porque
    // categorias/autores son catálogos de solo lectura desde este service
    // (no tienen reglas de negocio propias que ameriten un service
    // intermedio, a diferencia de LibroService en sí).
    private final CategoriaRepository categoriaRepo;
    private final AutorRepository autorRepo;

    public LibroService(LibroRepository libroRepo,
                        EditorialRepository editorialRepo,
                        IdiomaRepository idiomaRepo,
                        EstadoLibroRepository estadoRepo,
                        CategoriaRepository categoriaRepo,
                        AutorRepository autorRepo) {
        this.libroRepo     = libroRepo;
        this.editorialRepo = editorialRepo;
        this.idiomaRepo    = idiomaRepo;
        this.estadoRepo    = estadoRepo;
        this.categoriaRepo = categoriaRepo;
        this.autorRepo     = autorRepo;
    }

    @Cacheable("libros")
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listar(Pageable pageable) {
        return libroRepo.findByEstado_Nombre(ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    // Módulo 9.1: filtros de catálogo por categoría/autor
    // (LibroController ?categoriaId=/?autorId=). No lleva @Cacheable a
    // propósito: el cache "libros" ya cachea el listado sin filtro
    // (RedisCacheManager con una sola config por nombre de cache, ver
    // RedisConfig) y combinarlo con parámetros de filtro exigiría una key
    // compuesta que no está en el alcance de esta rama -- filtrar sin
    // cache es aceptable porque, a diferencia del listado general, no es
    // el path más transitado del catálogo.
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarPorCategoria(Integer categoriaId, Pageable pageable) {
        return libroRepo.findByCategorias_IdAndEstado_Nombre(categoriaId, ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarPorAutor(Long autorId, Pageable pageable) {
        return libroRepo.findByAutores_IdAndEstado_Nombre(autorId, ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorId(Long id) {
        return libroRepo.findById(id)
                .filter(l -> l.getEstado() != null && ESTADO_ACTIVO.equals(l.getEstado().getNombre()))
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
    }

    // Módulo 3 (RF-09/CU-08): autocompletado de catálogo. LibroSugerenciaDTO
    // (no LibroResponseDTO) a propósito: versión ligera para no mandar el
    // objeto completo en cada tecla presionada en el frontend. "disponible"
    // se deriva de stockDisponible > 0, no es una columna propia.
    // @Cacheable("sugerencias-libros"): cache propio, TTL corto (5-10s vía
    // app.cache.sugerencias.ttl-seconds, ver RedisConfig/application.yml)
    // -- separado del cache "libros" porque este necesita expirar mucho
    // más rápido (autocompletado por tecla, no un listado que cambia poco)
    // y porque la key acá es el texto de búsqueda, no la paginación.
    @Cacheable("sugerencias-libros")
    @Transactional(readOnly = true)
    public List<LibroSugerenciaDTO> sugerir(String texto) {
        EstadoLibro estadoActivo = estadoRepo.findByNombre(ESTADO_ACTIVO)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_libro sin fila '" + ESTADO_ACTIVO + "'"));
        return libroRepo.sugerirPorTitulo(texto, estadoActivo.getId()).stream()
                .map(l -> new LibroSugerenciaDTO(
                        l.getId(),
                        l.getTitulo(),
                        l.getStockDisponible() != null && l.getStockDisponible() > 0))
                .toList();
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO crear(LibroRequestDTO dto) {
        if (libroRepo.existsByIsbn(dto.isbn())) {
            throw new IllegalArgumentException(
                    "ISBN ya registrado: " + dto.isbn());
        }
        validarStock(dto.stockTotal(), dto.stockDisponible());
        return toDTO(libroRepo.save(fromDTO(dto)));
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO actualizar(Long id, LibroRequestDTO dto) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
        if (libroRepo.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new IllegalArgumentException(
                    "ISBN ya usado por otro libro: " + dto.isbn());
        }
        validarStock(dto.stockTotal(), dto.stockDisponible());

        libro.setTitulo(dto.titulo());
        libro.setIsbn(dto.isbn());
        libro.setResumen(dto.resumen());
        libro.setPortadaUrl(dto.portadaUrl());
        libro.setAnioPublicacion(dto.anioPublicacion().shortValue());
        libro.setStockTotal(dto.stockTotal().shortValue());
        libro.setStockDisponible(dto.stockDisponible().shortValue());
        libro.setEditorial(dto.editorialId() != null ? editorialRepo.getReferenceById(dto.editorialId()) : null);
        libro.setIdioma(dto.idiomaId() != null ? idiomaRepo.getReferenceById(dto.idiomaId()) : null);
        libro.setEstado(dto.estadoId() != null ? estadoRepo.getReferenceById(dto.estadoId()) : null);
        libro.setCategorias(resolverCategorias(dto.categoriaIds()));
        libro.setAutores(resolverAutores(dto.autorIds()));

        return toDTO(libroRepo.save(libro));
    }

    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public void eliminar(Long id) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
        EstadoLibro estadoDadoDeBaja = estadoRepo.findByNombre(ESTADO_DADO_DE_BAJA)
                .orElseThrow(() -> new IllegalStateException(
                        "Catalogo estados_libro sin fila '" + ESTADO_DADO_DE_BAJA + "'"));
        libro.setEstado(estadoDadoDeBaja);
        libroRepo.save(libro);
    }

    private void validarStock(Integer stockTotal, Integer stockDisponible) {
        if (stockTotal == null || stockDisponible == null) return;
        if (stockDisponible > stockTotal) {
            throw new IllegalArgumentException(
                    "El stock disponible no puede ser mayor al stock total");
        }
    }

    // getReferenceById por cada id, sin validar existencia una por una:
    // mismo criterio que editorialRepo.getReferenceById(...) arriba -- si
    // el id no existe, Hibernate lanza EntityNotFoundException recién al
    // hacer flush/save, no acá (referencia perezosa).
    private Set<Categoria> resolverCategorias(Set<Integer> categoriaIds) {
        if (categoriaIds == null || categoriaIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Categoria> categorias = new HashSet<>();
        for (Integer id : categoriaIds) {
            categorias.add(categoriaRepo.getReferenceById(id));
        }
        return categorias;
    }

    private Set<Autor> resolverAutores(Set<Long> autorIds) {
        if (autorIds == null || autorIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Autor> autores = new HashSet<>();
        for (Long id : autorIds) {
            autores.add(autorRepo.getReferenceById(id));
        }
        return autores;
    }

    private LibroResponseDTO toDTO(Libro l) {
        return new LibroResponseDTO(
                l.getId(),
                l.getTitulo(),
                l.getIsbn(),
                l.getResumen(),
                l.getPortadaUrl(),
                l.getAnioPublicacion() != null ? l.getAnioPublicacion().intValue() : null,
                l.getEditorial()  != null ? l.getEditorial().getId()     : null,
                l.getEditorial()  != null ? l.getEditorial().getNombre() : null,
                l.getIdioma()     != null ? l.getIdioma().getId()        : null,
                l.getIdioma()     != null ? l.getIdioma().getNombre()    : null,
                l.getEstado()     != null ? l.getEstado().getId()        : null,
                l.getEstado()     != null ? l.getEstado().getNombre()    : null,
                l.getStockTotal()      != null ? l.getStockTotal().intValue()      : null,
                l.getStockDisponible() != null ? l.getStockDisponible().intValue() : null,
                l.getUbicacionFisica(),
                l.getFechaRegistro(),
                l.getCategorias() == null ? List.of() :
                        l.getCategorias().stream().map(Categoria::getNombre).toList(),
                l.getAutores() == null ? List.of() :
                        l.getAutores().stream().map(Autor::getNombre).toList()
        );
    }

    private Libro fromDTO(LibroRequestDTO dto) {
        Libro l = new Libro();
        l.setTitulo(dto.titulo());
        l.setIsbn(dto.isbn());
        l.setResumen(dto.resumen());
        l.setPortadaUrl(dto.portadaUrl());
        l.setAnioPublicacion(dto.anioPublicacion().shortValue());
        l.setStockTotal(dto.stockTotal().shortValue());
        l.setStockDisponible(dto.stockDisponible().shortValue());
        l.setEditorial(editorialRepo.getReferenceById(dto.editorialId()));
        l.setIdioma(idiomaRepo.getReferenceById(dto.idiomaId()));
        l.setEstado(estadoRepo.getReferenceById(dto.estadoId()));
        l.setCategorias(resolverCategorias(dto.categoriaIds()));
        l.setAutores(resolverAutores(dto.autorIds()));
        return l;
    }
}