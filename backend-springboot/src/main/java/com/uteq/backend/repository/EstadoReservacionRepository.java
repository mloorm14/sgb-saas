package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoReservacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoReservacionRepository extends JpaRepository<EstadoReservacion, Integer> {

    Optional<EstadoReservacion> findByNombre(String nombre);
}