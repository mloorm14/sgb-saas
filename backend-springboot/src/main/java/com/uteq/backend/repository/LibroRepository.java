package com.uteq.backend.repository;

import com.uteq.backend.entity.Libro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

@Repository
public interface LibroRepository extends JpaRepository<Libro, Long> {

    Optional<Libro> findByIsbn(String isbn);

    // "activo" ya no existe como columna: el estado ACTIVO/DADO_DE_BAJA/...
    // vive en estados_libro (ver Libro.estado). findByEstado_Nombre navega
    // esa relación por nombre en vez de hardcodear el id del catálogo.
    Page<Libro> findByEstado_Nombre(String estadoNombre, Pageable pageable);

    List<Libro> findByTituloContainingIgnoreCaseAndEstado_Nombre(String titulo, String estadoNombre);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    // Módulo 9.1: filtros de catálogo por categoría/autor (LibroController
    // ?categoriaId=/?autorId=). "Categorias_Id"/"Autores_Id" navega la
    // colección @ManyToMany de Libro (ver Libro.categorias/Libro.autores),
    // mismo mecanismo de "propiedad anidada" que ya usa findByEstado_Nombre
    // con la relación @ManyToOne.
    Page<Libro> findByCategorias_IdAndEstado_Nombre(Integer categoriaId, String estadoNombre, Pageable pageable);

    Page<Libro> findByAutores_IdAndEstado_Nombre(Long autorId, String estadoNombre, Pageable pageable);

    // Filtros de libros (título/ISBN + categoría + autor + estado)
    Page<Libro> findByEstadoId(Integer estadoId, Pageable pageable);

    Page<Libro> findByCategorias_IdAndEstadoId(Integer categoriaId, Integer estadoId, Pageable pageable);

    Page<Libro> findByAutores_IdAndEstadoId(Long autorId, Integer estadoId, Pageable pageable);

    Page<Libro> findByCategorias_IdAndAutores_IdAndEstadoId(Integer categoriaId, Long autorId, Integer estadoId, Pageable pageable);

    // --- Queries nativas: isbn puede ser bytea o varchar en BD real ---
    // Todas usan isbn::text para兼容 ambos tipos (bytea y varchar).
    // Las queries anteriores eran JPQL con LOWER(l.isbn) que fallaba si
    // isbn es bytea ("function lower(bytea) does not exist").

    // Búsqueda por título O ISBN con estado específico
    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id = :estadoId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT count(*) FROM libros l "
            + "WHERE l.estado_id = :estadoId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Libro> buscarPorTextoOIsbn(@Param("q") String q, @Param("estadoId") Integer estadoId, Pageable pageable);

    // Búsqueda por título O ISBN + categoría
    @Query(value = "SELECT l.* FROM libros l "
            + "INNER JOIN libro_categorias lc ON lc.libro_id = l.id "
            + "WHERE l.estado_id = :estadoId "
            + "AND lc.categoria_id = :categoriaId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT count(*) FROM libros l "
            + "INNER JOIN libro_categorias lc ON lc.libro_id = l.id "
            + "WHERE l.estado_id = :estadoId "
            + "AND lc.categoria_id = :categoriaId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Libro> buscarPorTextoOIsbnYCategoria(@Param("q") String q, @Param("categoriaId") Integer categoriaId, @Param("estadoId") Integer estadoId, Pageable pageable);

    // Búsqueda por título O ISBN + autor
    @Query(value = "SELECT l.* FROM libros l "
            + "INNER JOIN libro_autores la ON la.libro_id = l.id "
            + "WHERE l.estado_id = :estadoId "
            + "AND la.autor_id = :autorId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT count(*) FROM libros l "
            + "INNER JOIN libro_autores la ON la.libro_id = l.id "
            + "WHERE l.estado_id = :estadoId "
            + "AND la.autor_id = :autorId "
            + "AND (LOWER(l.titulo) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<Libro> buscarPorTextoOIsbnYAutor(@Param("q") String q, @Param("autorId") Long autorId, @Param("estadoId") Integer estadoId, Pageable pageable);

    // Módulo 3 (búsqueda predictiva, RF-09/CU-08): similarity(...) es una
    // función de la extensión pg_trgm (ver
    // database/migrations/V6__busqueda_predictiva.sql), sin equivalente en
    // JPQL -- por eso va como @Query nativa, mismo patrón que las
    // funciones de PrestamoProcedureRepository. p_estado_id se resuelve en
    // LibroService (vía EstadoLibroRepository) en vez de hardcodear
    // 'ACTIVO' acá, siguiendo el mismo criterio que el resto del service
    // (ej. PrestamoService.idEstadoPrestamo). LIMIT 10 fijo: el resultado
    // es para un dropdown de autocompletado, no un listado paginado.
    @Query(value = "SELECT * FROM libros "
            + "WHERE estado_id = :p_estado_id AND similarity(titulo, :p_texto) > 0.1 "
            + "ORDER BY similarity(titulo, :p_texto) DESC "
            + "LIMIT 10", nativeQuery = true)
    List<Libro> sugerirPorTitulo(@Param("p_texto") String texto, @Param("p_estado_id") Integer estadoId);

    // buscarPendientes y buscarPorEstados: nativas con isbn::text.
    // Antes usaban @EntityGraph pero eso no funciona con nativeQuery.
    // Hibernate crea proxies para las relaciones lazy (editorial, idioma,
    // estado, categorias, autores) que se resuelven bajo la transacción
    // @Transactional del service que llama.
    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id = :estadoId "
            + "AND ( :q IS NULL "
            + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
            + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
            + "AND ( :anio IS NULL OR l.anio_publicacion = :anio )",
            countQuery = "SELECT count(*) FROM libros l "
                    + "WHERE l.estado_id = :estadoId "
                    + "AND ( :q IS NULL "
                    + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
                    + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
                    + "AND ( :anio IS NULL OR l.anio_publicacion = :anio )",
            nativeQuery = true)
    Page<Libro> buscarPendientes(@Param("q") String q, @Param("anio") Short anio, @Param("estadoId") Integer estadoId, Pageable pageable);

    @Query(value = "SELECT l.* FROM libros l "
            + "WHERE l.estado_id IN :estadoIds "
            + "AND ( :q IS NULL "
            + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
            + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
            + "AND ( :anio IS NULL OR l.anio_publicacion = :anio )",
            countQuery = "SELECT count(*) FROM libros l "
                    + "WHERE l.estado_id IN :estadoIds "
                    + "AND ( :q IS NULL "
                    + "      OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) "
                    + "      OR LOWER(l.isbn::text) LIKE LOWER(CONCAT('%', CONCAT(:q, '%'))) ) "
                    + "AND ( :anio IS NULL OR l.anio_publicacion = :anio )",
            nativeQuery = true)
    Page<Libro> buscarPorEstados(@Param("estadoIds") List<Integer> estadoIds, @Param("q") String q, @Param("anio") Short anio, Pageable pageable);
}
