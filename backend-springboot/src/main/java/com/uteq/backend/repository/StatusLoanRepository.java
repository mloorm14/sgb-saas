package com.uteq.backend.repository;

import com.uteq.backend.entity.StatusLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusLoanRepository extends JpaRepository<StatusLoan, Integer> {

    Optional<StatusLoan> findByName(String name);
}
