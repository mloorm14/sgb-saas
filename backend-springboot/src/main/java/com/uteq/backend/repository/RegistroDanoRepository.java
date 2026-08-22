package com.uteq.backend.repository;

import com.uteq.backend.entity.RegistroDano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroDanoRepository extends JpaRepository<RegistroDano, Long> {

    Optional<RegistroDano> findByPrestamoId(Long prestamoId);

    // Historial de devoluciones del bibliotecario: más recientes primero.
    // JOIN con prestamos para traer info del préstamo en la misma query.
    @Query("SELECT rd FROM RegistroDano rd "
            + "WHERE rd.bibliotecarioId = :bibliotecarioId "
            + "ORDER BY rd.fechaRegistro DESC")
    List<RegistroDano> findTop10ByBibliotecarioIdOrderByFechaRegistroDesc(
            @Param("bibliotecarioId") Long bibliotecarioId,
            org.springframework.data.domain.Pageable pageable);
}
