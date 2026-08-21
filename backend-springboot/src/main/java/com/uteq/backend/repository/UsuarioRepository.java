package com.uteq.backend.repository;

import com.uteq.backend.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

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
}
