package com.uteq.backend.repository;

import com.uteq.backend.entity.CategoriaDano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoriaDanoRepository extends JpaRepository<CategoriaDano, Integer> {
    Optional<CategoriaDano> findByNombre(String nombre);
}
