package com.uteq.backend.repository;

import com.uteq.backend.entity.RegistrationDamage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationDamageRepository extends JpaRepository<RegistrationDamage, Long> {

    Optional<RegistrationDamage> findByLoanId(Long loanId);

    // Historial de devoluciones del bibliotecario: más recientes primero.
    // JOIN con prestamos para traer info del préstamo en la misma query.
    @Query("SELECT rd FROM RegistrationDamage rd "
            + "WHERE rd.librarianId = :librarianId "
            + "ORDER BY rd.dateRegistration DESC")
    List<RegistrationDamage> findTop10ByLibrarianIdOrderByDateRegistrationDesc(
            @Param("librarianId") Long librarianId,
            org.springframework.data.domain.Pageable pageable);
}
