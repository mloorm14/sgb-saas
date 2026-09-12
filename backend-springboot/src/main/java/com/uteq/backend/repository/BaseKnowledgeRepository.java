package com.uteq.backend.repository;

import com.uteq.backend.entity.KnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaseKnowledgeRepository extends JpaRepository<KnowledgeBase, Integer> {

    List<KnowledgeBase> findByActiveTrue();
}
