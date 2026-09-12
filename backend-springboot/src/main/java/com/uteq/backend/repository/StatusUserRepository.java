package com.uteq.backend.repository;

import com.uteq.backend.entity.StatusUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusUserRepository extends JpaRepository<StatusUser, Integer> {

    Optional<StatusUser> findByName(String name);
}
