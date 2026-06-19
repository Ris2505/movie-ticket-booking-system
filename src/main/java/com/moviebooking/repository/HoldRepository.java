package com.moviebooking.repository;

import com.moviebooking.domain.entity.Hold;
import com.moviebooking.domain.enums.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {
    Optional<Hold> findByIdAndUserId(Long id, Long userId);
    Optional<Hold> findByIdAndStatus(Long id, HoldStatus status);
}
