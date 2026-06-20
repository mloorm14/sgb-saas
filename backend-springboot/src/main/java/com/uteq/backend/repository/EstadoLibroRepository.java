package com.uteq.backend.repository;

import com.uteq.backend.entity.EstadoLibro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadoLibroRepository extends JpaRepository<EstadoLibro, Integer> {}