package com.uteq.backend.repository;

import com.uteq.backend.entity.BackupSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackupScheduleRepository extends JpaRepository<BackupSchedule, Long> {

    // Listar programaciones activas ordenadas por última ejecución
    java.util.List<BackupSchedule> findByActiveTrueOrderByLastExecutionDesc();

    // Buscar una programación por ID y activo
    BackupSchedule findByIdAndActiveTrue(Long id);
}