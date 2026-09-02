package com.uteq.backend.repository;

import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.repository.projection.LibroMasPrestadoDetalladoProjection;
import com.uteq.backend.repository.projection.LibroMasPrestadoProjection;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import com.uteq.backend.repository.projection.ReporteCategoriasDemandadasProjection;
import com.uteq.backend.repository.projection.ReporteInventarioProjection;
import com.uteq.backend.repository.projection.ReporteMorosidadProjection;
import com.uteq.backend.repository.projection.ReporteUsoPorPeriodoProjection;
import com.uteq.backend.repository.projection.ReporteVencidosProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
// import org.springframework.data.jpa.repository.query.Procedure; -- ya no se
// usa: @Procedure generaba sintaxis PostgreSQL "nombre => valor" dentro del
// escape JDBC {call ...}, que pgjdbc no soporta ahí (bug conocido de
// Hibernate 6.2+/7.x sin fix oficial, ver
// docs/mediciones/backend/2026-07-28-fallo-invocacion-sp-multi-out.md y
// spring-projects/spring-data-jpa#3393). Reemplazado por @Query nativa,
// aprobado por el equipo -- pendiente de actualizar ADR-006.

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Invocación de los procedimientos/funciones de db/procs/ relacionados con
 * préstamos. No extiende JpaRepository a propósito: es un repositorio
 * "solo procedimientos" (patrón documentado de Spring Data JPA), por lo que
 * solo necesita el marcador {@link Repository}.
 */
@org.springframework.stereotype.Repository
public interface PrestamoProcedureRepository extends Repository<Prestamo, Long> {

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * sp_crear_prestamo: retorno escalar único (BIGINT) — caso simple,
    //  * mapea directo con @Procedure(procedureName=...) sin necesidad de
    //  * @NamedStoredProcedureQuery.
    //  */
    // @Procedure(procedureName = "sp_crear_prestamo")
    // Long spCrearPrestamo(
    //         @Param("p_usuario_id") Long usuarioId,
    //         @Param("p_libro_id") Long libroId,
    //         @Param("p_bibliotecario_id") Long bibliotecarioId,
    //         @Param("p_dias_prestamo") Integer diasPrestamo
    // );

    /**
     * sp_crear_prestamo: retorno escalar único (BIGINT). Antes usaba
     * @Procedure(procedureName=...) (ver bloque comentado arriba) -- fallaba
     * en runtime con "syntax error at or near '=>'" porque Hibernate genera
     * sintaxis de parámetros nombrados de PostgreSQL dentro del escape JDBC
     * {call ...}, que pgjdbc no soporta. @Query nativa con SELECT evita el
     * CallableStatement por completo (mismo patrón que ya usaba este
     * repositorio para las 2 funciones TABLE de abajo).
     */
    @Query(value = "SELECT sp_crear_prestamo(:p_usuario_id, :p_libro_id, :p_bibliotecario_id, :p_dias_prestamo)",
            nativeQuery = true)
    Long spCrearPrestamo(
            @Param("p_usuario_id") Long usuarioId,
            @Param("p_libro_id") Long libroId,
            @Param("p_bibliotecario_id") Long bibliotecarioId,
            @Param("p_dias_prestamo") Integer diasPrestamo
    );

    // ── CÓDIGO ANTERIOR (no usar, dejado como referencia histórica) ──
    // /**
    //  * sp_registrar_devolucion: la función tiene 3 parámetros OUT
    //  * (o_prestamo_id, o_hubo_multa, o_monto_multa). @Procedure con
    //  * procedureName directo no soporta bien múltiples OUT en PostgreSQL;
    //  * se resuelve con @NamedStoredProcedureQuery declarado en la entidad
    //  * {@link Prestamo} (name = "Prestamo.registrarDevolucion") y este
    //  * método devuelve un Map con una entrada por parámetro OUT, tal como
    //  * documenta Spring Data JPA para procedimientos multi-salida.
    //  */
    // @Procedure(name = "Prestamo.registrarDevolucion")
    // java.util.Map<String, Object> spRegistrarDevolucion(@Param("p_prestamo_id") Long prestamoId);

    /**
     * sp_registrar_devolucion: función con 3 parámetros OUT (o_prestamo_id,
     * o_hubo_multa, o_monto_multa). Antes resuelto vía
     * @NamedStoredProcedureQuery en {@link Prestamo} (ver bloque comentado
     * arriba y el @NamedStoredProcedureQuery comentado en Prestamo.java) --
     * mismo fallo de sintaxis "=>" que sp_crear_prestamo. @Query nativa con
     * "SELECT * FROM ..." expande el tipo compuesto de los OUT params en
     * columnas, y Map<String,Object> como tipo de retorno de un @Query
     * nativeQuery SÍ está soportado por Spring Data JPA ("native query
     * returning raw column name/value pairs", docs.spring.io) -- las keys
     * del Map son los nombres de columna (o_prestamo_id, o_hubo_multa,
     * o_monto_multa), igual que antes.
     */
    @Query(value = "SELECT * FROM sp_registrar_devolucion(:p_prestamo_id)", nativeQuery = true)
    java.util.Map<String, Object> spRegistrarDevolucion(@Param("p_prestamo_id") Long prestamoId);

    /**
     * fn_listar_prestamos_activos_por_usuario: función PostgreSQL con
     * RETURNS TABLE (varias filas). NO se pudo mapear con @Procedure ni
     * con @NamedStoredProcedureQuery: la API de stored procedures de JPA
     * 2.1 está construida sobre JDBC CallableStatement, que en PostgreSQL
     * solo expone resultados vía un valor escalar/OUT o un parámetro
     * REF_CURSOR — no vía RETURNS TABLE/SETOF invocado como función. Para
     * usar REF_CURSOR habría que reescribir la función para que abra y
     * retorne un cursor, lo cual le impediría seguir siendo invocable
     * directamente como "SELECT * FROM fn_...(...)" desde psql/otras
     * herramientas. Se usa en su lugar una @Query nativa (patrón estándar
     * de Spring Data para funciones PostgreSQL que retornan tabla).
     */
    @Query(value = "SELECT * FROM fn_listar_prestamos_activos_por_usuario(:p_usuario_id)", nativeQuery = true)
    List<PrestamoActivoProjection> fnListarPrestamosActivosPorUsuario(@Param("p_usuario_id") Long usuarioId);

    /**
     * fn_reporte_libros_mas_prestados: misma situación que
     * fn_listar_prestamos_activos_por_usuario (RETURNS TABLE de varias
     * filas) — @Query nativa por la misma razón documentada arriba.
     */
    @Query(value = "SELECT * FROM fn_reporte_libros_mas_prestados(:p_limite, :p_desde, :p_hasta)", nativeQuery = true)
    List<LibroMasPrestadoProjection> fnReporteLibrosMasPrestados(
            @Param("p_limite") Integer limite,
            @Param("p_desde") OffsetDateTime desde,
            @Param("p_hasta") OffsetDateTime hasta
    );

    /**
     * fn_reporte_indice_morosidad (Módulo 7): misma situación que
     * fn_reporte_libros_mas_prestados (RETURNS TABLE de varias filas) --
     * @Query nativa por la misma razón documentada arriba.
     */
    @Query(value = "SELECT * FROM fn_reporte_indice_morosidad(:p_limite)", nativeQuery = true)
    List<ReporteMorosidadProjection> fnReporteIndiceMorosidad(@Param("p_limite") Integer limite);

    /**
     * fn_reporte_uso_por_periodo (Módulo 7): idem, RETURNS TABLE.
     */
    @Query(value = "SELECT * FROM fn_reporte_uso_por_periodo(:p_granularidad, :p_desde, :p_hasta)",
            nativeQuery = true)
    List<ReporteUsoPorPeriodoProjection> fnReporteUsoPorPeriodo(
            @Param("p_granularidad") String granularidad,
            @Param("p_desde") OffsetDateTime desde,
            @Param("p_hasta") OffsetDateTime hasta
    );

    /**
     * fn_reporte_libros_mas_prestados_detallado: versión extendida con
     * autor, categoría y porcentaje del total.
     */
    @Query(value = "SELECT * FROM fn_reporte_libros_mas_prestados_detallado(:p_limite, :p_desde, :p_hasta, :p_categoria_id)",
            nativeQuery = true)
    List<LibroMasPrestadoDetalladoProjection> fnReporteLibrosMasPrestadosDetallado(
            @Param("p_limite") Integer limite,
            @Param("p_desde") OffsetDateTime desde,
            @Param("p_hasta") OffsetDateTime hasta,
            @Param("p_categoria_id") Integer categoriaId
    );

    @Query(value = "SELECT * FROM fn_reporte_inventario(:p_categoria_id, :p_estado_stock, :p_busqueda)",
            nativeQuery = true)
    List<ReporteInventarioProjection> fnReporteInventario(
            @Param("p_categoria_id") Integer categoriaId,
            @Param("p_estado_stock") String estadoStock,
            @Param("p_busqueda") String busqueda
    );

    @Query(value = "SELECT * FROM fn_reporte_prestamos_vencidos(:p_dias_atraso_min, :p_busqueda)",
            nativeQuery = true)
    List<ReporteVencidosProjection> fnReportePrestamosVencidos(
            @Param("p_dias_atraso_min") Integer diasAtrasoMin,
            @Param("p_busqueda") String busqueda
    );

    @Query(value = "SELECT * FROM fn_reporte_categorias_demandadas(:p_limite, :p_desde, :p_hasta)",
            nativeQuery = true)
    List<ReporteCategoriasDemandadasProjection> fnReporteCategoriasDemandadas(
            @Param("p_limite") Integer limite,
            @Param("p_desde") OffsetDateTime desde,
            @Param("p_hasta") OffsetDateTime hasta
    );
}