package com.uteq.backend.repository;

import com.uteq.backend.entity.StatusReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusReservationRepository extends JpaRepository<StatusReservation, Integer> {

    Optional<StatusReservation> findByName(String name);
}