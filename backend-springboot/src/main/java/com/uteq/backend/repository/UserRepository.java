package com.uteq.backend.repository;

import com.uteq.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    // Nota: usuarios no tiene columna "estado" (42703); el estado normalizado
    // es estado_id FK a estados_usuario (V2). Usar JOIN estados_usuario para
    // leer eu.nombre como estado (ver queries de morosidad).
    Optional<User> findByEmail(String email);

    // Autocompletado de usuarios por correo parcial (ventanilla de préstamos).
    // Retorna los 3 usuarios más coincidentes, ordenados por nombre.
    List<User> findTop3ByEmailContainingIgnoreCaseOrderByNameAsc(String email);

    // Usado por CredencialQrService.resolverPorToken() al leer un QR
    // escaneado en ventanilla.
    Optional<User> findByCredentialQrToken(UUID credentialQrToken);

    // Búsqueda por nombre o correo para el listado paginado de administración.
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    // F8-gerente (V38): listado con filtro opcional + creador opcional.
    // JPQL con LOWER+CONCAT evita el bug PostgreSQL+Hibernate con
    // CAST(:param AS TEXT) "syntax error at or near $1" (Position:33) en
    // nativeQuery cuando el param es NULL (ver 7e81c1e6 baseline).
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT u FROM User u WHERE "
                    + "(:filter IS NULL OR :filter = '' "
                    + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :filter, '%')) "
                    + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :filter, '%'))) "
                    + "AND (:createdBy IS NULL OR u.createdBy = :createdBy)")
    Page<User> searchWithFilters(
            @org.springframework.data.repository.query.Param("filter") String filter,
            @org.springframework.data.repository.query.Param("createdBy") Long createdBy,
            Pageable pageable);

    // Fix 404 Admin usuarios (Bloquear/Eliminar): fetch con JOIN FETCH evita
    // LazyInitialization en findById sin tocar paginación de buscarConFiltros.
    @org.springframework.data.jpa.repository.Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.status LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithStatusAndRoles(
            @org.springframework.data.repository.query.Param("id") Long id);

    // Case-insensitive para resolver ejecutor desde JWT (evita 404 fantasma si
    // correo viene con mayúsculas/espacios).
    Optional<User> findByEmailIgnoreCase(String email);

    // Auto-eliminación de cuentas no verificados: borra usuarios cuyo
    // correo no fue verificado dentro de las últimas 24 horas. Invocado
    // periódicamente por UsuarioScheduler.
    @org.springframework.data.jpa.repository.Query(
            value = "DELETE FROM usuarios WHERE correo_verificado = false AND fecha_registro < :cutoff",
            nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int deleteNotVerifiedsBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);


}
