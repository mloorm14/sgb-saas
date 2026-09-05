package com.uteq.backend.repository;

import com.uteq.backend.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Nota: usuarios no tiene columna "estado" (42703); el estado normalizado
    // es estado_id FK a estados_usuario (V2). Usar JOIN estados_usuario para
    // leer eu.nombre como estado (ver queries de morosidad).
    Optional<Usuario> findByCorreo(String correo);

    // Autocompletado de usuarios por correo parcial (ventanilla de préstamos).
    // Retorna los 3 usuarios más coincidentes, ordenados por nombre.
    List<Usuario> findTop3ByCorreoContainingIgnoreCaseOrderByNombreAsc(String correo);

    // Usado por CredencialQrService.resolverPorToken() al leer un QR
    // escaneado en ventanilla.
    Optional<Usuario> findByCredencialQrToken(UUID credencialQrToken);

    // Módulo 5 (panel de administración de usuarios): búsqueda por nombre
    // O correo, para el listado paginado que consume UsuarioAdminService.
    // "ContainingIgnoreCase" en ambos lados del OR, mismo mecanismo
    // derivado que ya usa LibroRepository.findByTituloContainingIgnoreCase...
    // -- no justifica una @Query nativa como sí la búsqueda con pg_trgm de
    // LibroRepository.sugerirPorTitulo (ese caso necesita ranking por
    // similitud; este es un filtro exacto de administración, no un
    // autocompletado).
    Page<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo, Pageable pageable);

    // F8-gerente (V38): listado con filtro opcional + creador opcional.
    // Nativa con CAST explícito para evitar el bug PostgreSQL+Hibernate
    // con parámetros NULL (ver skill sgb-backend-conventions).
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT * FROM usuarios u WHERE "
                    + "(CAST(:filtro AS TEXT) IS NULL OR CAST(:filtro AS TEXT) = '' "
                    + "OR u.nombre ILIKE '%' || CAST(:filtro AS TEXT) || '%' "
                    + "OR u.correo ILIKE '%' || CAST(:filtro AS TEXT) || '%') "
                    + "AND (CAST(:creadoPor AS BIGINT) IS NULL OR u.creado_por = CAST(:creadoPor AS BIGINT))",
            countQuery = "SELECT COUNT(*) FROM usuarios u WHERE "
                    + "(CAST(:filtro AS TEXT) IS NULL OR CAST(:filtro AS TEXT) = '' "
                    + "OR u.nombre ILIKE '%' || CAST(:filtro AS TEXT) || '%' "
                    + "OR u.correo ILIKE '%' || CAST(:filtro AS TEXT) || '%') "
                    + "AND (CAST(:creadoPor AS BIGINT) IS NULL OR u.creado_por = CAST(:creadoPor AS BIGINT))",
            nativeQuery = true)
    Page<Usuario> buscarConFiltros(
            @org.springframework.data.repository.query.Param("filtro") String filtro,
            @org.springframework.data.repository.query.Param("creadoPor") Long creadoPor,
            Pageable pageable);

    // Auto-eliminación de cuentas no verificados: borra usuarios cuyo
    // correo no fue verificado dentro de las últimas 24 horas. Invocado
    // periódicamente por UsuarioScheduler.
    @org.springframework.data.jpa.repository.Query(
            value = "DELETE FROM usuarios WHERE correo_verificado = false AND fecha_registro < :cutoff",
            nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int deleteNoVerificadosBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    // Lookup directo por PK sin filtros Hibernate (útil para admin cambiarEstado/eliminar
    // cuando el registro existe con estado INACTIVO/BLOQUEADO y un @Where hipotético lo ocultaría).
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM usuarios WHERE id = :id", nativeQuery = true)
    java.util.Optional<Usuario> findByIdNative(@org.springframework.data.repository.query.Param("id") Long id);
}
