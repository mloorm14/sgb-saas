package com.uteq.backend.repository;

import com.uteq.backend.entity.RegistroDanoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistroDanoDetalleRepository extends JpaRepository<RegistroDanoDetalle, Long> {

    List<RegistroDanoDetalle> findByRegistroDanoId(Long registroDanoId);
}
