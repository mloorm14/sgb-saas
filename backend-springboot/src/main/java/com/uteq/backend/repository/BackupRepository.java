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

    @Query("SELECT b FROM Backup b ORDER BY b.created DESC")
    List<Backup> findAllOrderByCreatedDesc();

    @Query("SELECT b FROM Backup b WHERE b.created >= :from AND b.created <= :until ORDER BY b.created DESC")
    List<Backup> findByDateRange(@Param("from") OffsetDateTime from, @Param("until") OffsetDateTime until);

    @Query("SELECT b FROM Backup b WHERE b.status = :status ORDER BY b.created DESC")
    List<Backup> findByStatus(@Param("status") String status);

    @Query("SELECT b FROM Backup b WHERE b.type = :type ORDER BY b.created DESC")
    List<Backup> findByType(@Param("type") String type);


}