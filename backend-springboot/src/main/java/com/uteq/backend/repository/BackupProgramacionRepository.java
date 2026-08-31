package com.uteq.backend.repository;

import com.uteq.backend.entity.BackupProgramacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupProgramacionRepository extends JpaRepository<BackupProgramacion, Long> {

    // Listar programaciones activas ordenadas por última ejecución
    java.util.List<BackupProgramacion> findByActivoOrderByUltimaEjecucionDesc();

    // Buscar una programación por ID y activo
    BackupProgramacion findByIdAndActivoTrue(Long id);
}