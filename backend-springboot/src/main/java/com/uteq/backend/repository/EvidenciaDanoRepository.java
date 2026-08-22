package com.uteq.backend.repository;

import com.uteq.backend.entity.EvidenciaDano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenciaDanoRepository extends JpaRepository<EvidenciaDano, Long> {

    List<EvidenciaDano> findByRegistroDanoId(Long registroDanoId);
}
