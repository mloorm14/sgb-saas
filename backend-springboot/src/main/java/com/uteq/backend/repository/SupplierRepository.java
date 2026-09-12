package com.uteq.backend.repository;

import com.uteq.backend.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Integer> {
    List<Supplier> findTop5ByNameContainingIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByRucIgnoreCase(String ruc);

    @Query("SELECT p FROM Supplier p WHERE (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.ruc) LIKE LOWER(CONCAT('%', :q, '%'))) AND (:active IS NULL OR p.active = :active)")
    Page<Supplier> searchWithFilters(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
