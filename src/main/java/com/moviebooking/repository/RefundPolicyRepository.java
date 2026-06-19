package com.moviebooking.repository;

import com.moviebooking.domain.entity.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {
}
