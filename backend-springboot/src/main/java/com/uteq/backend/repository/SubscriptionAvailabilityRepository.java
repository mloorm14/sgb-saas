package com.uteq.backend.repository;

import com.uteq.backend.entity.SubscriptionAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionAvailabilityRepository extends JpaRepository<SubscriptionAvailability, Long> {
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
    Optional<SubscriptionAvailability> findByUserIdAndBookId(Long userId, Long bookId);
    List<SubscriptionAvailability> findByBookId(Long bookId);
    List<SubscriptionAvailability> findByUserId(Long userId);
    void deleteByUserIdAndBookId(Long userId, Long bookId);
}
