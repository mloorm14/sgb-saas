package com.uteq.backend.repository;

import com.uteq.backend.entity.BaseConocimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaseConocimientoRepository extends JpaRepository<BaseConocimiento, Integer> {

    List<BaseConocimiento> findByActivoTrue();
}
