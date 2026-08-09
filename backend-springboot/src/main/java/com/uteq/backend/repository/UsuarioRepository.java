package com.uteq.backend.repository;

import com.uteq.backend.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    // Usado por CredencialQrService.resolverPorToken() al leer un QR
    // escaneado en ventanilla.
    Optional<Usuario> findByCredencialQrToken(UUID credencialQrToken);
}
