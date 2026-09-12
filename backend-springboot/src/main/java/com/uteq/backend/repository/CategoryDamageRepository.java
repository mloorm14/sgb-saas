package com.uteq.backend.repository;

import com.uteq.backend.entity.CategoryDamage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryDamageRepository extends JpaRepository<CategoryDamage, Integer> {
    Optional<CategoryDamage> findByName(String name);
}
