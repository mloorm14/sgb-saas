package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.entity.Autor;
import com.uteq.backend.entity.Categoria;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Proveedor;
import com.uteq.backend.repository.AutorRepository;
import com.uteq.backend.repository.CategoriaRepository;
import com.uteq.backend.repository.EditorialRepository;
import com.uteq.backend.repository.EstadoLibroRepository;
import com.uteq.backend.repository.IdiomaRepository;
import com.uteq.backend.repository.LibroRepository;
import com.uteq.backend.repository.ProveedorRepository;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LibroService {

    private static final Logger log = LoggerFactory.getLogger(LibroService.class);

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_DADO_DE_BAJA = "DADO_DE_BAJA";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    // Límite de portada (MB) en configuracion_sistema; el Admin lo ajusta sin despliegue.
    private static final String CLAVE_MAX_TAMANO_PORTADA_MB = "max_tamano_portada_mb";
    private static final List<String> TIPOS_PORTADA_PERMITIDOS =
            List.of("image/png", "image/jpeg", "image/webp", "image/avif");

    private final LibroRepository libroRepo;
    private final EditorialRepository editorialRepo;
    private final IdiomaRepository idiomaRepo;
    private final EstadoLibroRepository estadoRepo;
    // Categorías/autores/proveedor: catálogos inyectados directo (solo lectura desde este service).
    private final CategoriaRepository categoriaRepo;
    private final AutorRepository autorRepo;
    private final ProveedorRepository proveedorRepo;
    // Lee max_tamano_portada_mb con cache en memoria.
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final SuscripcionDisponibilidadService suscripcionDisponibilidadService;
    private final SugerenciaAdquisicionService sugerenciaAdquisicionService;

    // La auditoria de esta tabla ya no se hace aqui: trg_auditoria_libros
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    public LibroService(LibroRepository libroRepo,
                        EditorialRepository editorialRepo,
                        IdiomaRepository idiomaRepo,
                        EstadoLibroRepository estadoRepo,
                        CategoriaRepository categoriaRepo,
                        AutorRepository autorRepo,
                        ProveedorRepository proveedorRepo,
                        ConfiguracionSistemaService configuracionSistemaService,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) SuscripcionDisponibilidadService suscripcionDisponibilidadService,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) SugerenciaAdquisicionService sugerenciaAdquisicionService) {
        this.libroRepo     = libroRepo;
        this.editorialRepo = editorialRepo;
        this.idiomaRepo    = idiomaRepo;
        this.estadoRepo    = estadoRepo;
        this.categoriaRepo = categoriaRepo;
        this.autorRepo     = autorRepo;
        this.proveedorRepo = proveedorRepo;
        this.configuracionSistemaService = configuracionSistemaService;
        this.suscripcionDisponibilidadService = suscripcionDisponibilidadService;
        this.sugerenciaAdquisicionService = sugerenciaAdquisicionService;
    }

    /**
     * Devuelve la página de libros en estado ACTIVO para el catálogo general, usando la caché de listados.
     *
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de cada libro con editorial, idioma, estado, stock y nombres de categorías y autores
     */
    @Cacheable("libros")
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listar(Pageable pageable) {
        return libroRepo.findByEstado_Nombre(ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    /**
     * Devuelve la página de libros combinando texto, estado, categoría, autor y disponibilidad para las
     * búsquedas del catálogo. Elige la consulta adecuada según vengan texto o marca de disponibilidad y
     * resuelve el estado nulo al ACTIVO del catálogo.
     *
     * @param q texto libre para buscar por título o ISBN; nulo o vacío desactiva la búsqueda por texto
     * @param estadoLibroId identificador del estado a filtrar; nulo resuelve al ACTIVO del catálogo
     * @param categoriaId identificador de la categoría a filtrar; nulo desactiva ese filtro
     * @param autorId identificador del autor a filtrar; nulo desactiva ese filtro
     * @param disponible cuando es verdadero solo trae con stock disponible mayor a cero, cuando es falso solo agotados, nulo trae ambos
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros que cumplen los filtros combinados
     */
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarConFiltros(String q, Integer estadoLibroId, Integer categoriaId, Long autorId, Boolean disponible, Pageable pageable) {
        Integer estadoId = resolverEstadoId(estadoLibroId);

        // Con disponible y/o q, usar queries nativas con filtro stock
        if (q != null && !q.isBlank()) {
            if (categoriaId != null) {
                return libroRepo.buscarPorTextoOIsbnYCategoria(q, categoriaId, estadoId, disponible, pageable).map(this::toDTO);
            }
            return libroRepo.buscarPorTextoOIsbn(q, estadoId, disponible, pageable).map(this::toDTO);
        }

        if (disponible != null) {
            if (categoriaId != null) {
                if (disponible) {
                    return libroRepo.findByCategorias_IdAndEstadoIdAndStockDisponibleGreaterThan(categoriaId, estadoId, 0, pageable).map(this::toDTO);
                } else {
                    return libroRepo.findByCategorias_IdAndEstadoIdAndStockDisponibleEquals(categoriaId, estadoId, 0, pageable).map(this::toDTO);
                }
            }
            if (disponible) {
                return libroRepo.findByEstadoIdAndStockDisponibleGreaterThan(estadoId, 0, pageable).map(this::toDTO);
            } else {
                return libroRepo.findByEstadoIdAndStockDisponibleEquals(estadoId, 0, pageable).map(this::toDTO);
            }
        }

        if (categoriaId != null && autorId != null) {
            return libroRepo.findByCategorias_IdAndAutores_IdAndEstadoId(categoriaId, autorId, estadoId, pageable).map(this::toDTO);
        }
        if (categoriaId != null) {
            return libroRepo.findByCategorias_IdAndEstadoId(categoriaId, estadoId, pageable).map(this::toDTO);
        }
        if (autorId != null) {
            return libroRepo.findByAutores_IdAndEstadoId(autorId, estadoId, pageable).map(this::toDTO);
        }
        return libroRepo.findByEstadoId(estadoId, pageable).map(this::toDTO);
    }

    /**
     * Devuelve la página de libros con texto, estado, categoría y autor para compatibilidad con las
     * vistas que aún no filtran por disponibilidad. Delegada en la variante completa con disponibilidad
     * nula (ver {@link #listarConFiltros}).
     *
     * @param q texto libre para buscar por título o ISBN; nulo o vacío desactiva la búsqueda por texto
     * @param estadoLibroId identificador del estado a filtrar; nulo resuelve al ACTIVO del catálogo
     * @param categoriaId identificador de la categoría a filtrar; nulo desactiva ese filtro
     * @param autorId identificador del autor a filtrar; nulo desactiva ese filtro
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros que cumplen los filtros combinados
     */
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarConFiltros(String q, Integer estadoLibroId, Integer categoriaId, Long autorId, Pageable pageable) {
        return listarConFiltros(q, estadoLibroId, categoriaId, autorId, null, pageable);
    }

    private Integer resolverEstadoId(Integer estadoLibroId) {
        if (estadoLibroId != null) {
            return estadoLibroId;
        }
        return estadoRepo.findByNombre(ESTADO_ACTIVO)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_libro sin fila '" + ESTADO_ACTIVO + "'"))
                .getId();
    }

    /**
     * Devuelve la página de libros en estados operativos pendientes de revisión (dados de baja,
     * pendientes, en reparación o perdidos) para la bandeja de gestión interna. Si no se indican
     * estados usa los identificadores por defecto y ante un fallo de consulta lo registra y aborta.
     *
     * @param q texto libre para buscar dentro de esos estados; nulo desactiva la búsqueda por texto
     * @param anioPublicacion año de publicación a filtrar; nulo desactiva ese filtro
     * @param estadoIds identificadores de estado a incluir; nulos o vacíos usan los valores por defecto de la bandeja
     * @param pageable paginación y orden solicitados por la bandeja
     * @return página de vistas resumidas de los libros en los estados pedidos, vacía si la lista de estados queda vacía
     * @throws RuntimeException si la consulta de pendientes falla en el repositorio
     */
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarPendientes(String q, Integer anioPublicacion, List<Integer> estadoIds, Pageable pageable) {
        List<Integer> estados = resolverEstadosPendientes(estadoIds);
        if (estados.isEmpty()) {
            log.warn("listarPendientes: lista vacía - estadoIds={}", estadoIds);
            return Page.empty(pageable);
        }
        Short anioShort = anioPublicacion != null ? anioPublicacion.shortValue() : null;
        try {
            return libroRepo.buscarPorEstados(estados, q, anioShort, pageable).map(this::toDTO);
        } catch (Exception e) {
            log.error("listarPendientes error consultando {} libros con estados {}", estados.size(), q, e);
            throw new RuntimeException("Error interno al listar libros pendientes", e);
        }
    }

    private List<Integer> resolverEstadosPendientes(List<Integer> estadoIds) {
        if (estadoIds != null && !estadoIds.isEmpty()) {
            return estadoIds;
        }
        List<Integer> defaults = List.of(2, 3, 4, 5); // IDs por defecto: DADO_DE_BAJA, PENDIENTE, EN_REPARACION, PERDIDO
        log.warn("listarPendientes: usando estados por defecto, estadoIds={}", estadoIds);
        return defaults;
    }

    // Filtros de catálogo por categoría/autor (?categoriaId=/?autorId=). Sin @Cacheable: solo el listado general usa cache.
    /**
     * Devuelve la página de libros ACTIVO de una categoría para navegar el catálogo por estantería temática.
     *
     * @param categoriaId identificador de la categoría a explorar
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros ACTIVO vinculados a esa categoría
     */
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarPorCategoria(Integer categoriaId, Pageable pageable) {
        return libroRepo.findByCategorias_IdAndEstado_Nombre(categoriaId, ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    /**
     * Devuelve la página de libros ACTIVO de un autor para navegar el catálogo por autoría.
     *
     * @param autorId identificador del autor a explorar
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros ACTIVO vinculados a ese autor
     */
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listarPorAutor(Long autorId, Pageable pageable) {
        return libroRepo.findByAutores_IdAndEstado_Nombre(autorId, ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    /**
     * Recupera la ficha completa de un libro por su identificador para la gestión interna, sin importar
     * en qué estado se encuentre.
     *
     * @param id identificador del libro a recuperar
     * @return vista resumida del libro con editorial, idioma, estado, stock y nombres de categorías y autores
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     */
    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorId(Long id) {
        return libroRepo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
    }

    /**
     * Recupera la ficha de un libro visible al público para la página de detalle del catálogo, solo si
     * se encuentra en estado ACTIVO.
     *
     * @param id identificador del libro a mostrar en el catálogo público
     * @return vista resumida del libro en estado ACTIVO con editorial, idioma, stock y nombres de categorías y autores
     * @throws jakarta.persistence.EntityNotFoundException si no existe el libro o no está en estado ACTIVO
     */
    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorIdPublico(Long id) {
        return libroRepo.findById(id)
                .filter(l -> l.getEstado() != null && ESTADO_ACTIVO.equals(l.getEstado().getNombre()))
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
    }

    // Autocompletado: DTO ligero y "disponible" derivado de stockDisponible > 0. Cache propio de TTL corto.
    /**
     * Sugiere los candidatos cuyo título coincide con el texto para el autocompletado de búsqueda,
     * marcando en cada uno si hay stock disponible para préstamo inmediato. Usa caché propia de TTL corto.
     *
     * @param texto prefijo del título escrito por quien busca en el autocompletado
     * @return lista ligera de sugerencias con identificador, título y marca de disponibilidad derivada del stock
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
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

    /**
     * Da de alta un libro con sus catálogos y colecciones para ampliar el catálogo gestionable.
     * Rechaza ISBN duplicados y valida año, resumen, stock, páginas y precio; lo creado por GERENTE o
     * ADMIN queda en estado PENDIENTE, y el precio de lo creado por BIBLIOTECARIO se descarta. Al crear
     * con un ISBN pedido en sugerencias, esas quedan confirmadas.
     *
     * @param dto solicitud con título, ISBN, resumen, ubicación, año, páginas, existencias, precio base e identificadores de editorial, idioma, estado, categorías, autores y proveedor opcional
     * @return vista resumida del libro persistido con editorial, idioma, estado, stock y nombres de categorías y autores
     * @throws IllegalArgumentException si el ISBN ya está registrado o algún dato viola las reglas de año, resumen, stock, páginas o precio
     */
    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO crear(LibroRequestDTO dto) {
        if (libroRepo.existsByIsbn(dto.isbn())) {
            throw new IllegalArgumentException(
                    "ISBN ya registrado: " + dto.isbn());
        }
        validarAnio(dto.anioPublicacion());
        validarResumen(dto.resumen());
        validarStock(dto.stockTotal(), dto.stockDisponible());
        if (dto.numeroPaginas() != null && dto.numeroPaginas() <= 0) {
            throw new IllegalArgumentException("El número de páginas debe ser mayor a 0");
        }
        if (dto.precioBase() != null && dto.precioBase().signum() < 0) {
            throw new IllegalArgumentException("El precio base no puede ser negativo");
        }
        Libro libro = fromDTO(dto);
        if (esGerenteOAdmin() && dto.precioBase() != null) {
            // precio ya seteado en fromDTO; mantenerlo
        } else if (esGerenteOAdmin()) {
            // gerente/admin creando sin precio también va a pendiente según regla
        }
        if (esGerenteOAdmin()) {
            EstadoLibro pendiente = estadoRepo.findByNombre(ESTADO_PENDIENTE).orElse(null);
            if (pendiente != null) {
                libro.setEstado(pendiente);
            }
        }
        if (esBibliotecarioSolo() && libro.getPrecioBase() != null) {
            libro.setPrecioBase(null);
        }
        LibroResponseDTO resultado = toDTO(libroRepo.save(libro));
        // Al crear con ISBN pedido en sugerencias, esas quedan confirmadas (solo al crear).
        if (sugerenciaAdquisicionService != null && dto.isbn() != null && !dto.isbn().isBlank()) {
            sugerenciaAdquisicionService.confirmarAdquisicion(dto.isbn(), null);
        }
        return resultado;
    }

    /**
     * Reemplaza los datos del libro indicado para corregir su ficha o reponer existencias en el catálogo.
     * Rechaza ISBN usados por otro libro y valida año, resumen, stock y páginas; solo GERENTE o ADMIN
     * pueden vincular proveedor o tocar el precio base. Si el libro vuelve a tener stock, notifica a la
     * lista de espera como mecanismo best-effort.
     *
     * @param id identificador del libro cuya ficha se reemplaza
     * @param dto solicitud con título, ISBN, resumen, ubicación, año, páginas, existencias, precio base e identificadores de editorial, idioma, estado, categorías, autores y proveedor opcional
     * @return vista resumida del libro actualizado con editorial, idioma, estado, stock y nombres de categorías y autores
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     * @throws IllegalArgumentException si el ISBN pertenece a otro libro, el precio base es negativo o algún dato viola las reglas
     */
    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO actualizar(Long id, LibroRequestDTO dto) {
        Libro libro = libroRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
        int stockAntes = libro.getStockDisponible() != null ? libro.getStockDisponible() : 0;
        if (libroRepo.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new IllegalArgumentException(
                    "ISBN ya usado por otro libro: " + dto.isbn());
        }
        validarAnio(dto.anioPublicacion());
        validarResumen(dto.resumen());
        validarStock(dto.stockTotal(), dto.stockDisponible());
        if (dto.numeroPaginas() != null && dto.numeroPaginas() <= 0) {
            throw new IllegalArgumentException("El número de páginas debe ser mayor a 0");
        }

        libro.setTitulo(dto.titulo());
        libro.setIsbn(dto.isbn());
        libro.setResumen(dto.resumen());
        libro.setPortadaUrl(dto.portadaUrl());
        libro.setUbicacionFisica(dto.ubicacionFisica());
        libro.setAnioPublicacion(dto.anioPublicacion().shortValue());
        if (dto.numeroPaginas() != null) libro.setNumeroPaginas(dto.numeroPaginas().shortValue());
        else libro.setNumeroPaginas(null);
        libro.setStockTotal(dto.stockTotal().shortValue());
        libro.setStockDisponible(dto.stockDisponible().shortValue());
        libro.setEditorial(dto.editorialId() != null ? editorialRepo.getReferenceById(dto.editorialId()) : null);
        libro.setIdioma(dto.idiomaId() != null ? idiomaRepo.getReferenceById(dto.idiomaId()) : null);
        libro.setEstado(dto.estadoId() != null ? estadoRepo.getReferenceById(dto.estadoId()) : null);
        libro.setCategorias(resolverCategorias(dto.categoriaIds()));
        libro.setAutores(resolverAutores(dto.autorIds()));
        // Proveedor opcional: solo GERENTE/ADMIN pueden vincular (BIBLIOTECARIO -> S/P).
        if (dto.proveedorId() != null && esGerenteOAdmin()) {
            libro.setProveedor(proveedorRepo.getReferenceById(dto.proveedorId()));
        } else {
            libro.setProveedor(null);
        }
        // precioBase solo GERENTE/ADMIN puede modificar
        if (esGerenteOAdmin()) {
            if (dto.precioBase() != null && dto.precioBase().signum() < 0) {
                throw new IllegalArgumentException("El precio base no puede ser negativo");
            }
            libro.setPrecioBase(dto.precioBase());
        }
        // si es bibliotecario solo, ignorar dto.precioBase (no se modifica)

        LibroResponseDTO resultado = toDTO(libroRepo.save(libro));
        if (stockAntes == 0 && dto.stockDisponible() != null && dto.stockDisponible() > 0 && suscripcionDisponibilidadService != null) {
              try { suscripcionDisponibilidadService.notificarDisponibles(id); } catch (Exception ignored) {
                  // best-effort: la actualización del libro ya se guardó
              }
        }
        return resultado;
    }

    /**
     * Da de baja lógica el libro pasándolo al estado DADO_DE_BAJA para retirarlo del catálogo sin
     * borrar su fila ni su historial asociado.
     *
     * @param id identificador del libro a dar de baja
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     * @throws IllegalStateException si falta la fila de catálogo del estado DADO_DE_BAJA
     */
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

    // ── Portada binaria ──
    // POST portada (multipart): guarda el binario en BD y limpia portadaUrl para una sola fuente vigente.
    /**
     * Guarda la imagen de portada como binario en la base para unificar la visualización en una sola
     * fuente vigente, limpiando la URL externa previa. Invalida la caché de listados.
     *
     * @param libroId identificador del libro al que pertenece la portada
     * @param archivo imagen en PNG, JPEG, WEBP o AVIF dentro del tamaño máximo configurado en el sistema
     * @return vista resumida del libro con la portada actualizada
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     * @throws IllegalArgumentException si no se adjunta imagen, el tipo no está permitido, excede el tamaño máximo o no puede leerse
     */
    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public LibroResponseDTO actualizarPortada(Long libroId, MultipartFile archivo) {
        Libro libro = libroRepo.findById(libroId)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + libroId));
        validarPortada(archivo);
        try {
            libro.setPortadaImagen(archivo.getBytes());
        } catch (IOException ex) {
            // Fallo de lectura del archivo subido → 400, no 500.
            throw new IllegalArgumentException(
                    "No se pudo leer el archivo de portada: " + ex.getMessage());
        }
        libro.setPortadaNombre(archivo.getOriginalFilename());
        libro.setPortadaTipo(archivo.getContentType());
        libro.setPortadaTamanio((int) archivo.getSize());
        libro.setPortadaUrl(null);
        return toDTO(libroRepo.save(libro));
    }

    // GET portada: 404 si no existe o no tiene portada (el placeholder lo resuelve el frontend).
    /**
     * Recupera el binario y el tipo de la portada guardada para servir la imagen del libro al catálogo.
     * No genera marcador de posición: si no hay portada se informa como ausente.
     *
     * @param libroId identificador del libro cuya portada se quiere servir
     * @return contenedor con los bytes de la imagen y su tipo de contenido para la respuesta HTTP
     * @throws jakarta.persistence.EntityNotFoundException si no existe el libro o aún no tiene portada guardada
     */
    @Transactional(readOnly = true)
    public PortadaImagenDTO obtenerPortada(Long libroId) {
        Libro libro = libroRepo.findById(libroId)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + libroId));
        if (libro.getPortadaImagen() == null || libro.getPortadaTipo() == null) {
            throw new EntityNotFoundException("El libro con id: " + libroId + " no tiene portada");
        }
        return new PortadaImagenDTO(libro.getPortadaImagen(), libro.getPortadaTipo());
    }

    private void validarPortada(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de imagen");
        }
        String contentType = archivo.getContentType();
        if (contentType == null || !TIPOS_PORTADA_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de imagen no permitido: " + contentType
                            + ". Solo se admiten PNG, JPEG, WEBP y AVIF.");
        }
        int maxTamanoMb = configuracionSistemaService
                .obtenerValorEntero(CLAVE_MAX_TAMANO_PORTADA_MB);
        long maxTamanoBytes = maxTamanoMb * 1024L * 1024L;
        if (archivo.getSize() > maxTamanoBytes) {
            throw new IllegalArgumentException(
                    "La imagen excede el tamaño máximo permitido de "
                            + maxTamanoMb + " MB");
        }
    }

    private void validarStock(Integer stockTotal, Integer stockDisponible) {
        if (stockTotal == null || stockDisponible == null) return;
        if (stockDisponible > stockTotal) {
            throw new IllegalArgumentException(
                    "El stock disponible no puede ser mayor al stock total");
        }
    }

    private void validarAnio(Integer anio) {
        if (anio == null) return;
        int max = java.time.Year.now().getValue() + 1;
        if (anio < 1950 || anio > max) {
            throw new IllegalArgumentException("El año debe estar entre 1950 y " + max);
        }
    }

    private void validarResumen(String resumen) {
        if (resumen != null && resumen.length() > 2000) {
            throw new IllegalArgumentException("El resumen no puede superar 2000 caracteres");
        }
    }

    private boolean esGerenteOAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean esBibliotecarioSolo() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean isBiblio = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BIBLIOTECARIO"));
        boolean isGerenteAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE") || a.getAuthority().equals("ROLE_ADMIN"));
        return isBiblio && !isGerenteAdmin;
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

    private Set<Autor> resolverAutores(Set<Integer> autorIds) {
        if (autorIds == null || autorIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Autor> autores = new HashSet<>();
        for (Integer id : autorIds) {
            autores.add(autorRepo.getReferenceById(id.longValue()));
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
                l.getPortadaImagen() != null,
                l.getPortadaNombre(),
                l.getPortadaTipo(),
                l.getAnioPublicacion() != null ? l.getAnioPublicacion().intValue() : null,
                l.getNumeroPaginas() != null ? l.getNumeroPaginas().intValue() : null,
                l.getPrecioBase(),
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
                        l.getAutores().stream().map(Autor::getNombre).toList(),
                l.getProveedor() != null ? l.getProveedor().getId() : null,
                l.getProveedor() != null ? l.getProveedor().getNombre() : null
        );
    }

    private Libro fromDTO(LibroRequestDTO dto) {
        Libro l = new Libro();
        l.setTitulo(dto.titulo());
        l.setIsbn(dto.isbn());
        l.setResumen(dto.resumen());
        l.setPortadaUrl(dto.portadaUrl());
        l.setUbicacionFisica(dto.ubicacionFisica());
        l.setAnioPublicacion(dto.anioPublicacion().shortValue());
        if (dto.numeroPaginas() != null) l.setNumeroPaginas(dto.numeroPaginas().shortValue());
        l.setPrecioBase(dto.precioBase());
        l.setStockTotal(dto.stockTotal().shortValue());
        l.setStockDisponible(dto.stockDisponible().shortValue());
        l.setEditorial(editorialRepo.getReferenceById(dto.editorialId()));
        l.setIdioma(idiomaRepo.getReferenceById(dto.idiomaId()));
        l.setEstado(estadoRepo.getReferenceById(dto.estadoId()));
        l.setCategorias(resolverCategorias(dto.categoriaIds()));
        l.setAutores(resolverAutores(dto.autorIds()));
        // Proveedor opcional: solo GERENTE/ADMIN pueden vincular (BIBLIOTECARIO -> S/P).
        if (dto.proveedorId() != null && esGerenteOAdmin()) {
            l.setProveedor(proveedorRepo.getReferenceById(dto.proveedorId()));
        } else {
            l.setProveedor(null);
        }
        return l;
    }
}