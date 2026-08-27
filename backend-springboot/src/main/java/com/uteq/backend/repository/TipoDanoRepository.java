package com.uteq.backend.repository;

import com.uteq.backend.entity.TipoDano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TipoDanoRepository extends JpaRepository<TipoDano, Integer> {

    List<TipoDano> findByActivoTrue();

    Optional<TipoDano> findByNombre(String nombre);

    List<TipoDano> findByActivoTrueAndCategoriaId(Integer categoriaId);
}
