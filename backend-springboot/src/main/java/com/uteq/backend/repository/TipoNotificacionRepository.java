package com.uteq.backend.repository;

import com.uteq.backend.entity.TipoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TipoNotificacionRepository extends JpaRepository<TipoNotificacion, Integer> {

    Optional<TipoNotificacion> findByNombre(String nombre);
}
