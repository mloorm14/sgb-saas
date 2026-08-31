package com.uteq.backend.repository;

import com.uteq.backend.entity.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.OffsetDateTime;

@Repository
public interface BackupRepository extends JpaRepository<Backup, Long> {

    @Query("SELECT b FROM Backup b ORDER BY b.creadoEn DESC")
    List<Backup> findAllOrderByCreatedDesc();

    @Query("SELECT b FROM Backup b WHERE b.creadoEn >= :desde AND b.creadoEn <= :hasta ORDER BY b.creadoEn DESC")
    List<Backup> findByFechaRange(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);

    @Query("SELECT b FROM Backup b WHERE b.estado = :estado ORDER BY b.creadoEn DESC")
    List<Backup> findByEstado(@Param("estado") String estado);


}