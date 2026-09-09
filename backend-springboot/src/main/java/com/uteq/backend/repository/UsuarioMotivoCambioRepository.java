package com.uteq.backend.repository;

import com.uteq.backend.entity.UsuarioMotivoCambio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioMotivoCambioRepository extends JpaRepository<UsuarioMotivoCambio, Long> {

    // Historial de motivos del usuario: más recientes primero.
    List<UsuarioMotivoCambio> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
