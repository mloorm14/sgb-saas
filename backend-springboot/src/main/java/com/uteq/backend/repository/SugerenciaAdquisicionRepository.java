package com.uteq.backend.repository;

import com.uteq.backend.entity.SugerenciaAdquisicion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SugerenciaAdquisicionRepository extends JpaRepository<SugerenciaAdquisicion, Long> {

    Page<SugerenciaAdquisicion> findByUsuarioId(Long usuarioId, Pageable pageable);

    Page<SugerenciaAdquisicion> findByEstado(String estado, Pageable pageable);
}
