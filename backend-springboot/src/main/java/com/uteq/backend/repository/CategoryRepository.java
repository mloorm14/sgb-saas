package com.uteq.backend.repository;

import com.uteq.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findTop5ByNameContainingIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
