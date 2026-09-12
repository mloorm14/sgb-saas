package com.uteq.backend.repository;

import com.uteq.backend.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublisherRepository extends JpaRepository<Publisher, Integer> {
    List<Publisher> findTop5ByNameContainingIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}