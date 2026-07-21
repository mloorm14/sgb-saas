package com.uteq.backend.repository;

import com.uteq.backend.entity.Prestamo;
import com.uteq.backend.repository.projection.LibroMasPrestadoProjection;
import com.uteq.backend.repository.projection.PrestamoActivoProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.query.Procedure;

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

    /**
     * sp_crear_prestamo: retorno escalar único (BIGINT) — caso simple,
     * mapea directo con @Procedure(procedureName=...) sin necesidad de
     * @NamedStoredProcedureQuery.
     */
    @Procedure(procedureName = "sp_crear_prestamo")
    Long spCrearPrestamo(
            @Param("p_usuario_id") Long usuarioId,
            @Param("p_libro_id") Long libroId,
            @Param("p_bibliotecario_id") Long bibliotecarioId,
            @Param("p_dias_prestamo") Integer diasPrestamo
    );

    /**
     * sp_registrar_devolucion: la función tiene 3 parámetros OUT
     * (o_prestamo_id, o_hubo_multa, o_monto_multa). @Procedure con
     * procedureName directo no soporta bien múltiples OUT en PostgreSQL;
     * se resuelve con @NamedStoredProcedureQuery declarado en la entidad
     * {@link Prestamo} (name = "Prestamo.registrarDevolucion") y este
     * método devuelve un Map con una entrada por parámetro OUT, tal como
     * documenta Spring Data JPA para procedimientos multi-salida.
     */
    @Procedure(name = "Prestamo.registrarDevolucion")
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
     * de Spring Data para funciones PostgreSQL que retornan tabla) —
     * evaluar con el equipo si esta desviación de @Procedure/
     * @NamedStoredProcedureQuery es aceptable antes de pasarlo a Cajas.
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
}
