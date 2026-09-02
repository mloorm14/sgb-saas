package com.uteq.backend.repository;

import com.uteq.backend.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

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

    // Auto-eliminación de cuentas no verificados: borra usuarios cuyo
    // correo no fue verificado dentro de las últimas 24 horas. Invocado
    // periódicamente por UsuarioScheduler.
    @org.springframework.data.jpa.repository.Query(
            value = "DELETE FROM usuarios WHERE correo_verificado = false AND fecha_registro < :cutoff",
            nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int deleteNoVerificadosBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
