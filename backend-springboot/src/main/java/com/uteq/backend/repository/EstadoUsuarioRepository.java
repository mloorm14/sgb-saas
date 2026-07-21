package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoUsuarioRepository extends JpaRepository<EstadoUsuario, Integer> {

    Optional<EstadoUsuario> findByNombre(String nombre);
}
