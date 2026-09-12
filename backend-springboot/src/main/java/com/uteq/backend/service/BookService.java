package com.uteq.backend.service;

import com.uteq.backend.dto.BookRequestDTO;
import com.uteq.backend.dto.BookResponseDTO;
import com.uteq.backend.dto.BookSuggestionDTO;
import com.uteq.backend.dto.CoverImageDTO;
import com.uteq.backend.entity.Author;
import com.uteq.backend.entity.Category;
import com.uteq.backend.entity.StatusBook;
import com.uteq.backend.entity.Book;
import com.uteq.backend.entity.Supplier;
import com.uteq.backend.repository.AuthorRepository;
import com.uteq.backend.repository.CategoryRepository;
import com.uteq.backend.repository.PublisherRepository;
import com.uteq.backend.repository.StatusBookRepository;
import com.uteq.backend.repository.LanguageRepository;
import com.uteq.backend.repository.BookRepository;
import com.uteq.backend.repository.SupplierRepository;
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
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private static final String LIBRO_NO_ENCONTRADO = "Libro no encontrado con id: ";
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_DADO_DE_BAJA = "DADO_DE_BAJA";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    // Límite de portada (MB) en configuracion_sistema; el Admin lo ajusta sin despliegue.
    private static final String CLAVE_MAX_TAMANO_PORTADA_MB = "max_tamano_portada_mb";
    private static final List<String> TIPOS_PORTADA_PERMITIDOS =
            List.of("image/png", "image/jpeg", "image/webp", "image/avif");

    private final BookRepository bookRepo;
    private final PublisherRepository publisherRepo;
    private final LanguageRepository languageRepo;
    private final StatusBookRepository statusRepo;
    // Categorías/autores/proveedor: catálogos inyectados directo (solo lectura desde este service).
    private final CategoryRepository categoryRepo;
    private final AuthorRepository authorRepo;
    private final SupplierRepository supplierRepo;
    // Lee max_tamano_portada_mb con cache en memoria.
    private final ConfigurationSystemService configurationSystemService;
    private final SubscriptionAvailabilityService subscriptionAvailabilityService;
    private final SuggestionAcquisitionService suggestionAcquisitionService;

    // La auditoria de esta tabla ya no se hace aqui: trg_auditoria_libros
    // (V49__auditoria_triggers_negocio.sql) audita INSERT/UPDATE/DELETE a nivel de motor.
    public BookService(BookRepository bookRepo,
                        PublisherRepository publisherRepo,
                        LanguageRepository languageRepo,
                        StatusBookRepository statusRepo,
                        CategoryRepository categoryRepo,
                        AuthorRepository authorRepo,
                        SupplierRepository supplierRepo,
                        ConfigurationSystemService configurationSystemService,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) SubscriptionAvailabilityService subscriptionAvailabilityService,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) SuggestionAcquisitionService suggestionAcquisitionService) {
        this.bookRepo     = bookRepo;
        this.publisherRepo = publisherRepo;
        this.languageRepo    = languageRepo;
        this.statusRepo    = statusRepo;
        this.categoryRepo = categoryRepo;
        this.authorRepo     = authorRepo;
        this.supplierRepo = supplierRepo;
        this.configurationSystemService = configurationSystemService;
        this.subscriptionAvailabilityService = subscriptionAvailabilityService;
        this.suggestionAcquisitionService = suggestionAcquisitionService;
    }

    /**
     * Devuelve la página de libros en estado ACTIVO para el catálogo general, usando la caché de listados.
     *
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de cada libro con editorial, idioma, estado, stock y nombres de categorías y autores
     */
    @Cacheable("libros")
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> list(Pageable pageable) {
        return bookRepo.findByStatus_Name(ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    /**
     * Devuelve la página de libros combinando texto, estado, categoría, autor y disponibilidad para las
     * búsquedas del catálogo. Elige la consulta adecuada según vengan texto o marca de disponibilidad y
     * resuelve el estado nulo al ACTIVO del catálogo.
     *
     * @param q texto libre para buscar por título o ISBN; nulo o vacío desactiva la búsqueda por texto
     * @param statusBookId identificador del estado a filtrar; nulo resuelve al ACTIVO del catálogo
     * @param categoryId identificador de la categoría a filtrar; nulo desactiva ese filtro
     * @param authorId identificador del autor a filtrar; nulo desactiva ese filtro
     * @param available cuando es verdadero solo trae con stock disponible mayor a cero, cuando es falso solo agotados, nulo trae ambos
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros que cumplen los filtros combinados
     */
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> listWithFilters(String q, Integer statusBookId, Integer categoryId, Long authorId, Boolean available, Pageable pageable) {
        Integer statusId = resolveStatusId(statusBookId);

        // Con disponible y/o q, usar queries nativas con filtro stock
        if (q != null && !q.isBlank()) {
            if (categoryId != null) {
                return bookRepo.searchByTextOIsbnYCategory(q, categoryId, statusId, available, pageable).map(this::toDTO);
            }
            return bookRepo.searchByTextOIsbn(q, statusId, available, pageable).map(this::toDTO);
        }

        if (available != null) {
            if (categoryId != null) {
                if (available) {
                    return bookRepo.findByCategories_IdAndStatusIdAndStockAvailableGreaterThan(categoryId, statusId, 0, pageable).map(this::toDTO);
                } else {
                    return bookRepo.findByCategories_IdAndStatusIdAndStockAvailableEquals(categoryId, statusId, 0, pageable).map(this::toDTO);
                }
            }
            if (available) {
                return bookRepo.findByStatusIdAndStockAvailableGreaterThan(statusId, 0, pageable).map(this::toDTO);
            } else {
                return bookRepo.findByStatusIdAndStockAvailableEquals(statusId, 0, pageable).map(this::toDTO);
            }
        }

        if (categoryId != null && authorId != null) {
            return bookRepo.findByCategories_IdAndAuthors_IdAndStatusId(categoryId, authorId, statusId, pageable).map(this::toDTO);
        }
        if (categoryId != null) {
            return bookRepo.findByCategories_IdAndStatusId(categoryId, statusId, pageable).map(this::toDTO);
        }
        if (authorId != null) {
            return bookRepo.findByAuthors_IdAndStatusId(authorId, statusId, pageable).map(this::toDTO);
        }
        return bookRepo.findByStatusId(statusId, pageable).map(this::toDTO);
    }

    /**
     * Devuelve la página de libros con texto, estado, categoría y autor para compatibilidad con las
     * vistas que aún no filtran por disponibilidad. Delegada en la variante completa con disponibilidad
     * nula (ver {@link #listWithFilters}).
     *
     * @param q texto libre para buscar por título o ISBN; nulo o vacío desactiva la búsqueda por texto
     * @param statusBookId identificador del estado a filtrar; nulo resuelve al ACTIVO del catálogo
     * @param categoryId identificador de la categoría a filtrar; nulo desactiva ese filtro
     * @param authorId identificador del autor a filtrar; nulo desactiva ese filtro
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros que cumplen los filtros combinados
     */
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> listWithFilters(String q, Integer statusBookId, Integer categoryId, Long authorId, Pageable pageable) {
        return listWithFilters(q, statusBookId, categoryId, authorId, null, pageable);
    }

    private Integer resolveStatusId(Integer statusBookId) {
        if (statusBookId != null) {
            return statusBookId;
        }
        return statusRepo.findByName(ESTADO_ACTIVO)
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
     * @param yearPublication año de publicación a filtrar; nulo desactiva ese filtro
     * @param statusIds identificadores de estado a incluir; nulos o vacíos usan los valores por defecto de la bandeja
     * @param pageable paginación y orden solicitados por la bandeja
     * @return página de vistas resumidas de los libros en los estados pedidos, vacía si la lista de estados queda vacía
     * @throws RuntimeException si la consulta de pendientes falla en el repositorio
     */
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> listPending(String q, Integer yearPublication, List<Integer> statusIds, Pageable pageable) {
        List<Integer> statuses = resolvePendingStatuses(statusIds);
        if (statuses.isEmpty()) {
            log.warn("listarPendientes: lista vacía - estadoIds={}", statusIds);
            return Page.empty(pageable);
        }
        Short yearShort = yearPublication != null ? yearPublication.shortValue() : null;
        try {
            return bookRepo.searchByStatuses(statuses, q, yearShort, pageable).map(this::toDTO);
        } catch (Exception e) {
            log.error("listarPendientes error consultando {} libros con estados {}", statuses.size(), q, e);
            throw new RuntimeException("Error interno al listar libros pendientes", e);
        }
    }

    private List<Integer> resolvePendingStatuses(List<Integer> statusIds) {
        if (statusIds != null && !statusIds.isEmpty()) {
            return statusIds;
        }
        List<Integer> defaults = List.of(2, 3, 4, 5); // IDs por defecto: DADO_DE_BAJA, PENDIENTE, EN_REPARACION, PERDIDO
        log.warn("listarPendientes: usando estados por defecto, estadoIds={}", statusIds);
        return defaults;
    }

    // Filtros de catálogo por categoría/autor (?categoriaId=/?autorId=). Sin @Cacheable: solo el listado general usa cache.
    /**
     * Devuelve la página de libros ACTIVO de una categoría para navegar el catálogo por estantería temática.
     *
     * @param categoryId identificador de la categoría a explorar
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros ACTIVO vinculados a esa categoría
     */
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> listByCategory(Integer categoryId, Pageable pageable) {
        return bookRepo.findByCategories_IdAndStatus_Name(categoryId, ESTADO_ACTIVO, pageable)
                .map(this::toDTO);
    }

    /**
     * Devuelve la página de libros ACTIVO de un autor para navegar el catálogo por autoría.
     *
     * @param authorId identificador del autor a explorar
     * @param pageable paginación y orden solicitados por el catálogo
     * @return página de vistas resumidas de los libros ACTIVO vinculados a ese autor
     */
    @Transactional(readOnly = true)
    public Page<BookResponseDTO> listByAuthor(Long authorId, Pageable pageable) {
        return bookRepo.findByAuthors_IdAndStatus_Name(authorId, ESTADO_ACTIVO, pageable)
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
    public BookResponseDTO searchById(Long id) {
        return bookRepo.findById(id)
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
    public BookResponseDTO searchByIdPublic(Long id) {
        return bookRepo.findById(id)
                .filter(l -> l.getStatus() != null && ESTADO_ACTIVO.equals(l.getStatus().getName()))
                .map(this::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
    }

    // Autocompletado: DTO ligero y "disponible" derivado de stockDisponible > 0. Cache propio de TTL corto.
    /**
     * Sugiere los candidatos cuyo título coincide con el texto para el autocompletado de búsqueda,
     * marcando en cada uno si hay stock disponible para préstamo inmediato. Usa caché propia de TTL corto.
     *
     * @param text prefijo del título escrito por quien busca en el autocompletado
     * @return lista ligera de sugerencias con identificador, título y marca de disponibilidad derivada del stock
     * @throws IllegalStateException si falta la fila de catálogo del estado ACTIVO
     */
    @Cacheable("sugerencias-libros")
    @Transactional(readOnly = true)
    public List<BookSuggestionDTO> sugerir(String text) {
        StatusBook statusActive = statusRepo.findByName(ESTADO_ACTIVO)
                .orElseThrow(() -> new IllegalStateException(
                        "Catálogo estados_libro sin fila '" + ESTADO_ACTIVO + "'"));
        return bookRepo.sugerirByTitle(text, statusActive.getId()).stream()
                .map(l -> new BookSuggestionDTO(
                        l.getId(),
                        l.getTitle(),
                        l.getStockAvailable() != null && l.getStockAvailable() > 0))
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
    public BookResponseDTO create(BookRequestDTO dto) {
        if (bookRepo.existsByIsbn(dto.isbn())) {
            throw new IllegalArgumentException(
                    "ISBN ya registrado: " + dto.isbn());
        }
        validateYear(dto.yearPublication());
        validateSummary(dto.summary());
        validateStock(dto.stockTotal(), dto.stockAvailable());
        if (dto.numberPages() != null && dto.numberPages() <= 0) {
            throw new IllegalArgumentException("El número de páginas debe ser mayor a 0");
        }
        if (dto.priceBase() != null && dto.priceBase().signum() < 0) {
            throw new IllegalArgumentException("El precio base no puede ser negativo");
        }
        Book book = fromDTO(dto);
        if (esManagerOAdmin() && dto.priceBase() != null) {
            // precio ya seteado en fromDTO; mantenerlo
        } else if (esManagerOAdmin()) {
            // gerente/admin creando sin precio también va a pendiente según regla
        }
        if (esManagerOAdmin()) {
            StatusBook pending = statusRepo.findByName(ESTADO_PENDIENTE).orElse(null);
            if (pending != null) {
                book.setStatus(pending);
            }
        }
        if (esLibrarianSolo() && book.getPriceBase() != null) {
            book.setPriceBase(null);
        }
        BookResponseDTO result = toDTO(bookRepo.save(book));
        // Al crear con ISBN pedido en sugerencias, esas quedan confirmadas (solo al crear).
        if (suggestionAcquisitionService != null && dto.isbn() != null && !dto.isbn().isBlank()) {
            suggestionAcquisitionService.confirmAcquisition(dto.isbn(), null);
        }
        return result;
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
    public BookResponseDTO update(Long id, BookRequestDTO dto) {
        Book book = bookRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
        int stockAntes = book.getStockAvailable() != null ? book.getStockAvailable() : 0;
        if (bookRepo.existsByIsbnAndIdNot(dto.isbn(), id)) {
            throw new IllegalArgumentException(
                    "ISBN ya usado por otro libro: " + dto.isbn());
        }
        validateYear(dto.yearPublication());
        validateSummary(dto.summary());
        validateStock(dto.stockTotal(), dto.stockAvailable());
        if (dto.numberPages() != null && dto.numberPages() <= 0) {
            throw new IllegalArgumentException("El número de páginas debe ser mayor a 0");
        }

        book.setTitle(dto.title());
        book.setIsbn(dto.isbn());
        book.setSummary(dto.summary());
        book.setCoverUrl(dto.coverUrl());
        book.setLocationPhysical(dto.locationPhysical());
        book.setYearPublication(dto.yearPublication().shortValue());
        if (dto.numberPages() != null) book.setNumberPages(dto.numberPages().shortValue());
        else book.setNumberPages(null);
        book.setStockTotal(dto.stockTotal().shortValue());
        book.setStockAvailable(dto.stockAvailable().shortValue());
        book.setPublisher(dto.publisherId() != null ? publisherRepo.getReferenceById(dto.publisherId()) : null);
        book.setLanguage(dto.languageId() != null ? languageRepo.getReferenceById(dto.languageId()) : null);
        book.setStatus(dto.statusId() != null ? statusRepo.getReferenceById(dto.statusId()) : null);
        book.setCategories(resolveCategories(dto.categoryIds()));
        book.setAuthors(resolveAuthors(dto.authorIds()));
        // Proveedor opcional: solo GERENTE/ADMIN pueden vincular (BIBLIOTECARIO -> S/P).
        if (dto.supplierId() != null && esManagerOAdmin()) {
            book.setSupplier(supplierRepo.getReferenceById(dto.supplierId()));
        } else {
            book.setSupplier(null);
        }
        // precioBase solo GERENTE/ADMIN puede modificar
        if (esManagerOAdmin()) {
            if (dto.priceBase() != null && dto.priceBase().signum() < 0) {
                throw new IllegalArgumentException("El precio base no puede ser negativo");
            }
            book.setPriceBase(dto.priceBase());
        }
        // si es bibliotecario solo, ignorar dto.precioBase (no se modifica)

        BookResponseDTO result = toDTO(bookRepo.save(book));
        if (stockAntes == 0 && dto.stockAvailable() != null && dto.stockAvailable() > 0 && subscriptionAvailabilityService != null) {
              try { subscriptionAvailabilityService.notifyDisponibles(id); } catch (Exception ignored) {
                  // best-effort: la actualización del libro ya se guardó
              }
        }
        return result;
    }

    /**
     * Da de baja lógica el libro pasándolo al estado DADO_DE_BAJA para retirarlo del catálogo sin
     * deleteBackup su fila ni su historial asociado.
     *
     * @param id identificador del libro a dar de baja
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     * @throws IllegalStateException si falta la fila de catálogo del estado DADO_DE_BAJA
     */
    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public void delete(Long id) {
        Book book = bookRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + id));
        StatusBook statusDadoRemoval = statusRepo.findByName(ESTADO_DADO_DE_BAJA)
                .orElseThrow(() -> new IllegalStateException(
                        "Catalogo estados_libro sin fila '" + ESTADO_DADO_DE_BAJA + "'"));
        book.setStatus(statusDadoRemoval);
        bookRepo.save(book);
    }

    // ── Portada binaria ──
    // POST portada (multipart): guarda el binario en BD y limpia portadaUrl para una sola fuente vigente.
    /**
     * Guarda la imagen de portada como binario en la base para unificar la visualización en una sola
     * fuente vigente, limpiando la URL externa previa. Invalida la caché de listados.
     *
     * @param bookId identificador del libro al que pertenece la portada
     * @param file imagen en PNG, JPEG, WEBP o AVIF dentro del tamaño máximo configurado en el sistema
     * @return vista resumida del libro con la portada actualizada
     * @throws jakarta.persistence.EntityNotFoundException si no existe ningún libro con ese identificador
     * @throws IllegalArgumentException si no se adjunta imagen, el tipo no está permitido, excede el tamaño máximo o no puede leerse
     */
    @CacheEvict(value = "libros", allEntries = true)
    @Transactional
    public BookResponseDTO updateCover(Long bookId, MultipartFile file) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + bookId));
        validateCover(file);
        try {
            book.setCoverImage(file.getBytes());
        } catch (IOException ex) {
            // Fallo de lectura del archivo subido → 400, no 500.
            throw new IllegalArgumentException(
                    "No se pudo leer el archivo de portada: " + ex.getMessage());
        }
        book.setCoverName(file.getOriginalFilename());
        book.setCoverType(file.getContentType());
        book.setCoverTamanio((int) file.getSize());
        book.setCoverUrl(null);
        return toDTO(bookRepo.save(book));
    }

    // GET portada: 404 si no existe o no tiene portada (el placeholder lo resuelve el frontend).
    /**
     * Recupera el binario y el tipo de la portada guardada para servir la imagen del libro al catálogo.
     * No genera marcador de posición: si no hay portada se informa como ausente.
     *
     * @param bookId identificador del libro cuya portada se quiere servir
     * @return contenedor con los bytes de la imagen y su tipo de contenido para la respuesta HTTP
     * @throws jakarta.persistence.EntityNotFoundException si no existe el libro o aún no tiene portada guardada
     */
    @Transactional(readOnly = true)
    public CoverImageDTO getCover(Long bookId) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException(
                        LIBRO_NO_ENCONTRADO + bookId));
        if (book.getCoverImage() == null || book.getCoverType() == null) {
            throw new EntityNotFoundException("El libro con id: " + bookId + " no tiene portada");
        }
        return new CoverImageDTO(book.getCoverImage(), book.getCoverType());
    }

    private void validateCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de imagen");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TIPOS_PORTADA_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de imagen no permitido: " + contentType
                            + ". Solo se admiten PNG, JPEG, WEBP y AVIF.");
        }
        int maxSizeMb = configurationSystemService
                .getValueEntero(CLAVE_MAX_TAMANO_PORTADA_MB);
        long maxSizeBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "La imagen excede el tamaño máximo permitido de "
                            + maxSizeMb + " MB");
        }
    }

    private void validateStock(Integer stockTotal, Integer stockAvailable) {
        if (stockTotal == null || stockAvailable == null) return;
        if (stockAvailable > stockTotal) {
            throw new IllegalArgumentException(
                    "El stock disponible no puede ser mayor al stock total");
        }
    }

    private void validateYear(Integer year) {
        if (year == null) return;
        int max = java.time.Year.now().getValue() + 1;
        if (year < 1950 || year > max) {
            throw new IllegalArgumentException("El año debe estar entre 1950 y " + max);
        }
    }

    private void validateSummary(String summary) {
        if (summary != null && summary.length() > 2000) {
            throw new IllegalArgumentException("El resumen no puede superar 2000 caracteres");
        }
    }

    private boolean esManagerOAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean esLibrarianSolo() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean isBiblio = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_BIBLIOTECARIO"));
        boolean isManagerAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE") || a.getAuthority().equals("ROLE_ADMIN"));
        return isBiblio && !isManagerAdmin;
    }

    // getReferenceById por cada id, sin validar existencia una por una:
    // mismo criterio que editorialRepo.getReferenceById(...) arriba -- si
    // el id no existe, Hibernate lanza EntityNotFoundException recién al
    // hacer flush/save, no acá (referencia perezosa).
    private Set<Category> resolveCategories(Set<Integer> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Category> categories = new HashSet<>();
        for (Integer id : categoryIds) {
            categories.add(categoryRepo.getReferenceById(id));
        }
        return categories;
    }

    private Set<Author> resolveAuthors(Set<Integer> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Author> authors = new HashSet<>();
        for (Integer id : authorIds) {
            authors.add(authorRepo.getReferenceById(id.longValue()));
        }
        return authors;
    }

    private BookResponseDTO toDTO(Book l) {
        return new BookResponseDTO(
                l.getId(),
                l.getTitle(),
                l.getIsbn(),
                l.getSummary(),
                l.getCoverUrl(),
                l.getCoverImage() != null,
                l.getCoverName(),
                l.getCoverType(),
                l.getYearPublication() != null ? l.getYearPublication().intValue() : null,
                l.getNumberPages() != null ? l.getNumberPages().intValue() : null,
                l.getPriceBase(),
                l.getPublisher()  != null ? l.getPublisher().getId()     : null,
                l.getPublisher()  != null ? l.getPublisher().getName() : null,
                l.getLanguage()     != null ? l.getLanguage().getId()        : null,
                l.getLanguage()     != null ? l.getLanguage().getName()    : null,
                l.getStatus()     != null ? l.getStatus().getId()        : null,
                l.getStatus()     != null ? l.getStatus().getName()    : null,
                l.getStockTotal()      != null ? l.getStockTotal().intValue()      : null,
                l.getStockAvailable() != null ? l.getStockAvailable().intValue() : null,
                l.getLocationPhysical(),
                l.getDateRegistration(),
                l.getCategories() == null ? List.of() :
                        l.getCategories().stream().map(Category::getName).toList(),
                l.getAuthors() == null ? List.of() :
                        l.getAuthors().stream().map(Author::getName).toList(),
                l.getSupplier() != null ? l.getSupplier().getId() : null,
                l.getSupplier() != null ? l.getSupplier().getName() : null
        );
    }

    private Book fromDTO(BookRequestDTO dto) {
        Book l = new Book();
        l.setTitle(dto.title());
        l.setIsbn(dto.isbn());
        l.setSummary(dto.summary());
        l.setCoverUrl(dto.coverUrl());
        l.setLocationPhysical(dto.locationPhysical());
        l.setYearPublication(dto.yearPublication().shortValue());
        if (dto.numberPages() != null) l.setNumberPages(dto.numberPages().shortValue());
        l.setPriceBase(dto.priceBase());
        l.setStockTotal(dto.stockTotal().shortValue());
        l.setStockAvailable(dto.stockAvailable().shortValue());
        l.setPublisher(publisherRepo.getReferenceById(dto.publisherId()));
        l.setLanguage(languageRepo.getReferenceById(dto.languageId()));
        l.setStatus(statusRepo.getReferenceById(dto.statusId()));
        l.setCategories(resolveCategories(dto.categoryIds()));
        l.setAuthors(resolveAuthors(dto.authorIds()));
        // Proveedor opcional: solo GERENTE/ADMIN pueden vincular (BIBLIOTECARIO -> S/P).
        if (dto.supplierId() != null && esManagerOAdmin()) {
            l.setSupplier(supplierRepo.getReferenceById(dto.supplierId()));
        } else {
            l.setSupplier(null);
        }
        return l;
    }
}