package com.uteq.backend.repository;

import com.uteq.backend.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    List<Proveedor> findTop5ByNombreContainingIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByRucIgnoreCase(String ruc);

    @Query("SELECT p FROM Proveedor p WHERE (:q IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.ruc) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:activo IS NULL OR p.activo = :activo)")
    Page<Proveedor> buscarConFiltros(@Param("q") String q, @Param("activo") Boolean activo, Pageable pageable);
}
