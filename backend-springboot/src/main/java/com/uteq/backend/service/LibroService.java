package com.uteq.backend.service;

import com.uteq.backend.dto.LibroRequestDTO;
import com.uteq.backend.dto.LibroResponseDTO;
import com.uteq.backend.dto.LibroSugerenciaDTO;
import com.uteq.backend.dto.PortadaImagenDTO;
import com.uteq.backend.entity.Autor;
import com.uteq.backend.entity.BitacoraAuditoria;
import com.uteq.backend.entity.Categoria;
import com.uteq.backend.entity.EstadoLibro;
import com.uteq.backend.entity.Libro;
import com.uteq.backend.entity.Proveedor;
import com.uteq.backend.repository.AutorRepository;
import com.uteq.backend.repository.BitacoraAuditoriaRepository;
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
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LibroService {

    private static final Logger log = LoggerFactory.getLogger(LibroService.class);

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String TABLA_LIBROS = "libros";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_DADO_DE_BAJA = "DADO_DE_BAJA";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    // Módulo portada binaria: límite de tamaño (MB) en configuracion_sistema
    // (misma clave que inserta V13__portada_imagen.sql), no hardcodeada acá
    // -- el Admin la ajusta sin despliegue nuevo vía ConfiguracionSistema.
    private static final String CLAVE_MAX_TAMANO_PORTADA_MB = "max_tamano_portada_mb";
    private static final List<String> TIPOS_PORTADA_PERMITIDOS =
            List.of("image/png", "image/jpeg", "image/webp", "image/avif");

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
    private final ProveedorRepository proveedorRepo;
    // Módulo portada binaria: para leer max_tamano_portada_mb con cache en
    // memoria (ver ConfiguracionSistemaService), mismo patrón que
    // PrestamoService con dias_prestamo_default/max_renovaciones_default.
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final BitacoraAuditoriaRepository bitacoraAuditoriaRepo;
    private final SuscripcionDisponibilidadService suscripcionDisponibilidadService;

    public LibroService(LibroRepository libroRepo,
                        EditorialRepository editorialRepo,
                        IdiomaRepository idiomaRepo,
                        EstadoLibroRepository estadoRepo,
                        CategoriaRepository categoriaRepo,
                        AutorRepository autorRepo,
                        ProveedorRepository proveedorRepo,
                        ConfiguracionSistemaService configuracionSistemaService,
                        BitacoraAuditoriaRepository bitacoraAuditoriaRepo,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) SuscripcionDisponibilidadService suscripcionDisponibilidadService) {
        this.libroRepo     = libroRepo;
        this.editorialRepo = editorialRepo;
        this.idiomaRepo    = idiomaRepo;
        this.estadoRepo    = estadoRepo;
        this.categoriaRepo = categoriaRepo;
        this.autorRepo     = autorRepo;
        this.proveedorRepo = proveedorRepo;
        this.configuracionSistemaService = configuracionSistemaService;
        this.bitacoraAuditoriaRepo = bitacoraAuditoriaRepo;
        this.suscripcionDisponibilidadService = suscripcionDisponibilidadService;
    }

    private void registrarAuditoria(Long usuarioId, String tipoOperacion, Long registroId, String detalles) {
        BitacoraAuditoria evento = BitacoraAuditoria.builder()
                .usuarioId(usuarioId)
                .tipoOperacion(tipoOperacion)
                .tablaAfectada(TABLA_LIBROS)
                .registroId(registroId)
                .detalles(detalles)
                .fechaHora(OffsetDateTime.now())
                .build();
        bitacoraAuditoriaRepo.save(evento);
    }

    @Cacheable("libros")
    @Transactional(readOnly = true)
    public Page<LibroResponseDTO> listar(Pageable pageable) {
        return libroRepo.findByEstado_Nombre(ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

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
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
    }

    @Transactional(readOnly = true)
    public LibroResponseDTO buscarPorIdPublico(Long id) {
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
        registrarAuditoria(null, "INSERT", resultado.id(), "Libro creado: " + dto.titulo());
        return resultado;
    }

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
        registrarAuditoria(null, "UPDATE", id, "Libro actualizado: " + dto.titulo());
        if (stockAntes == 0 && dto.stockDisponible() != null && dto.stockDisponible() > 0 && suscripcionDisponibilidadService != null) {
              try { suscripcionDisponibilidadService.notificarDisponibles(id); } catch (Exception ignored) {
                  // best-effort: la actualización del libro ya se guardó
              }
        }
        return resultado;
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
        registrarAuditoria(null, "DELETE", id, "Libro dado de baja: " + libro.getTitulo());
    }

    // ── Portada binaria (V13__portada_imagen.sql) ─────────────
    // POST /api/v1/libros/{id}/portada (multipart/form-data). Guarda el
    // binario dentro de la BD y limpia portadaUrl a null: una vez que la
    // portada vive en la base, una URL externa que el sistema no controla
    // ya no tiene razón de ser -- si quedara, el frontend no sabría cuál
    // es la fuente vigente.
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
            // MultipartFile.getBytes() sobre un archivo ya transferido no
            // debería fallar; si lo hace, es un problema de la request, no
            // del libro -- 400, no 500.
            throw new IllegalArgumentException(
                    "No se pudo leer el archivo de portada: " + ex.getMessage());
        }
        libro.setPortadaNombre(archivo.getOriginalFilename());
        libro.setPortadaTipo(archivo.getContentType());
        libro.setPortadaTamanio((int) archivo.getSize());
        libro.setPortadaUrl(null);
        return toDTO(libroRepo.save(libro));
    }

    // GET /api/v1/libros/{id}/portada. Devuelve el binario junto al
    // Content-Type dinámico (portada_tipo) para que el controller defina
    // el header de la respuesta. 404 si el libro no existe O no tiene
    // portada -- el placeholder lo resuelve el frontend, no el backend.
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