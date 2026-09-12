package com.uteq.backend.repository;

import com.uteq.backend.entity.ConfigurationBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationBackupRepository extends JpaRepository<ConfigurationBackup, Long> {
}
