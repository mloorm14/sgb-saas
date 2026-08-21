package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoMulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoMultaRepository extends JpaRepository<EstadoMulta, Integer> {

    Optional<EstadoMulta> findByNombre(String nombre);
}
