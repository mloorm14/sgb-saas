package com.uteq.backend.repository;

import com.uteq.backend.entity.RegistroRespaldo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistroRespaldoRepository extends JpaRepository<RegistroRespaldo, Long> {
    List<RegistroRespaldo> findByTipoOrderByIniciadoEnDesc(String tipo);
}
