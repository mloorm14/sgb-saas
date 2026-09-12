package com.uteq.backend.repository;

import com.uteq.backend.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    // "activo" ya no existe como columna: el estado ACTIVO/DADO_DE_BAJA/...
    // vive en estados_libro (ver Libro.estado). findByEstado_Nombre navega
    // esa relación por nombre en vez de hardcodear el id del catálogo.
    Page<Book> findByStatus_Name(String statusName, Pageable pageable);

    List<Book> findByTitleContainingIgnoreCaseAndStatus_Name(String title, String statusName);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    // Filtros de catálogo por categoría/autor (navega las colecciones @ManyToMany de Libro).
    Page<Book> findByCategories_IdAndStatus_Name(Integer categoryId, String statusName, Pageable pageable);

    Page<Book> findByAuthors_IdAndStatus_Name(Long authorId, String statusName, Pageable pageable);

    // Filtros de libros (título/ISBN + categoría + autor + estado)
    Page<Book> findByStatusId(Integer statusId, Pageable pageable);

    Page<Book> findByStatusIdAndStockAvailableGreaterThan(Integer statusId, int stock, Pageable pageable);

    Page<Book> findByStatusIdAndStockAvailableEquals(Integer statusId, int stock, Pageable pageable);

    Page<Book> findByCategories_IdAndStatusId(Integer categoryId, Integer statusId, Pageable pageable);

    Page<Book> findByCategories_IdAndStatusIdAndStockAvailableGreaterThan(Integer categoryId, Integer statusId, int stock, Pageable pageable);

    Page<Book> findByCategories_IdAndStatusIdAndStockAvailableEquals(Integer categoryId, Integer statusId, int stock, Pageable pageable);

    Page<Book> findByAuthors_IdAndStatusId(Long authorId, Integer statusId, Pageable pageable);

    Page<Book> findByCategories_IdAndAuthors_IdAndStatusId(Integer categoryId, Long authorId, Integer statusId, Pageable pageable);

    // --- Queries nativas: isbn puede ser bytea o varchar en BD real ---
    // Todas usan isbn::text para兼容 ambos tipos (bytea y varchar).
    // Las queries anteriores eran JPQL con LOWER(l.isbn) que fallaba si
    // isbn es bytea ("function lower(bytea) does not exist").

    // Búsqueda por título O ISBN con estado específico + filtro opcional disponible
    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id = :statusId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))"
            + "AND (:available IS NULL OR (:available = true AND l.stock_disponible > 0) OR (:available = false AND l.stock_disponible = 0))",
            countQuery = "SELECT count(*) FROM libros l "
            + "WHERE l.estado_id = :statusId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))"
            + "AND (:available IS NULL OR (:available = true AND l.stock_disponible > 0) OR (:available = false AND l.stock_disponible = 0))",
            nativeQuery = true)
    Page<Book> searchByTextOIsbn(@Param("q") String q, @Param("statusId") Integer statusId, @Param("available") Boolean available, Pageable pageable);

    // Búsqueda por título O ISBN + categoría (+ disponible)
    @Query(value = "SELECT l.* FROM libros l "
            + "INNER JOIN libro_categorias lc ON lc.libro_id = l.id "
            + "WHERE l.estado_id = :statusId "
            + "AND lc.categoria_id = :categoryId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))"
            + "AND (:available IS NULL OR (:available = true AND l.stock_disponible > 0) OR (:available = false AND l.stock_disponible = 0))",
            countQuery = "SELECT count(*) FROM libros l "
            + "INNER JOIN libro_categorias lc ON lc.libro_id = l.id "
            + "WHERE l.estado_id = :statusId "
            + "AND lc.categoria_id = :categoryId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))"
            + "AND (:available IS NULL OR (:available = true AND l.stock_disponible > 0) OR (:available = false AND l.stock_disponible = 0))",
            nativeQuery = true)
    Page<Book> searchByTextOIsbnYCategory(@Param("q") String q, @Param("categoryId") Integer categoryId, @Param("statusId") Integer statusId, @Param("available") Boolean available, Pageable pageable);

    // Búsqueda por título O ISBN + autor
    @Query(value = "SELECT l.* FROM libros l "
            + "INNER JOIN libro_autores la ON la.libro_id = l.id "
            + "WHERE l.estado_id = :statusId "
            + "AND la.autor_id = :authorId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT count(*) FROM libros l "
            + "INNER JOIN libro_autores la ON la.libro_id = l.id "
            + "WHERE l.estado_id = :statusId "
            + "AND la.autor_id = :authorId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Book> searchByTextOIsbnYAuthor(@Param("q") String q, @Param("authorId") Long authorId, @Param("statusId") Integer statusId, Pageable pageable);

    // Búsqueda por similitud con pg_trgm (top 10 por similarity de título, para autocompletado).
    @Query(value = "SELECT * FROM libros "
            + "WHERE estado_id = :p_estado_id AND similarity(titulo, :p_texto) > 0.1 "
            + "ORDER BY similarity(titulo, :p_texto) DESC "
            + "LIMIT 10", nativeQuery = true)
    List<Book> sugerirByTitle(@Param("p_texto") String text, @Param("p_estado_id") Integer statusId);

    // buscarPendientes y buscarPorEstados: nativas con isbn::text.
    // Antes usaban @EntityGraph pero eso no funciona con nativeQuery.
    // Hibernate crea proxies para las relaciones lazy (editorial, idioma,
    // estado, categorias, autores) que se resuelven bajo la transacción
    // @Transactional del service que llama.
    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id = :statusId "
            + "AND ( :q IS NULL "
            + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
            + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
            + "AND ( :year IS NULL OR l.anio_publicacion = :year )",
            countQuery = "SELECT count(*) FROM libros l "
                    + "WHERE l.estado_id = :statusId "
                    + "AND ( :q IS NULL "
                    + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
                    + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
                    + "AND ( :year IS NULL OR l.anio_publicacion = :year )",
            nativeQuery = true)
    Page<Book> searchPendientes(@Param("q") String q, @Param("year") Short year, @Param("statusId") Integer statusId, Pageable pageable);

    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id IN :statusIds "
            + "AND ( :q IS NULL "
            + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
            + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
            + "AND ( :year IS NULL OR l.anio_publicacion = :year )",
            countQuery = "SELECT count(*) FROM libros l "
                    + "WHERE l.estado_id IN :statusIds "
                    + "AND ( :q IS NULL "
                    + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
                    + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
                    + "AND ( :year IS NULL OR l.anio_publicacion = :year )",
            nativeQuery = true)
    Page<Book> searchByStatuses(@Param("statusIds") List<Integer> statusIds, @Param("q") String q, @Param("year") Short year, Pageable pageable);
}
