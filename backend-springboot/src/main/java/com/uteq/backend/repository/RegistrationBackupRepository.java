package com.uteq.backend.repository;

import com.uteq.backend.entity.RegistrationBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationBackupRepository extends JpaRepository<RegistrationBackup, Long> {
    List<RegistrationBackup> findByTypeOrderByStartedDesc(String type);
}
