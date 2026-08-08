package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoPrestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoPrestamoRepository extends JpaRepository<EstadoPrestamo, Integer> {

    Optional<EstadoPrestamo> findByNombre(String nombre);
}
