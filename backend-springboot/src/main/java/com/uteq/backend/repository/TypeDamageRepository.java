package com.uteq.backend.repository;

import com.uteq.backend.entity.TypeDamage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TypeDamageRepository extends JpaRepository<TypeDamage, Integer> {

    List<TypeDamage> findByActiveTrue();

    Optional<TypeDamage> findByName(String name);

    List<TypeDamage> findByActiveTrueAndCategoryId(Integer categoryId);
}
