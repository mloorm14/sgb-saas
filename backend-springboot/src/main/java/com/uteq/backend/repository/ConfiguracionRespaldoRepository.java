package com.uteq.backend.repository;

import com.uteq.backend.entity.ConfiguracionRespaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionRespaldoRepository extends JpaRepository<ConfiguracionRespaldo, Long> {
}
