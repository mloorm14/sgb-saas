package com.uteq.backend.repository;

import com.uteq.backend.entity.SuscripcionDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SuscripcionDisponibilidadRepository extends JpaRepository<SuscripcionDisponibilidad, Long> {
    boolean existsByUsuarioIdAndLibroId(Long usuarioId, Long libroId);
    Optional<SuscripcionDisponibilidad> findByUsuarioIdAndLibroId(Long usuarioId, Long libroId);
    List<SuscripcionDisponibilidad> findByLibroId(Long libroId);
    List<SuscripcionDisponibilidad> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioIdAndLibroId(Long usuarioId, Long libroId);
}
