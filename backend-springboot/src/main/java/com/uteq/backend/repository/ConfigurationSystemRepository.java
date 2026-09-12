package com.uteq.backend.repository;

import com.uteq.backend.entity.ConfigurationSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationSystemRepository extends JpaRepository<ConfigurationSystem, String> {
}
