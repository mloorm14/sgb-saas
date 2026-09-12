package com.uteq.backend.repository;

import com.uteq.backend.entity.UserReasonChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReasonChangeRepository extends JpaRepository<UserReasonChange, Long> {

    // Historial de motivos del usuario: más recientes primero.
    List<UserReasonChange> findByUserIdOrderByCreatedDesc(Long userId);
}
