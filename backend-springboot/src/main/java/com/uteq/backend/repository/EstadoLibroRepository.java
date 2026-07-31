package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoLibro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoLibroRepository extends JpaRepository<EstadoLibro, Integer> {

    Optional<EstadoLibro> findByNombre(String nombre);
}