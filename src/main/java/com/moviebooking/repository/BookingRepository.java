package com.moviebooking.repository;

import com.moviebooking.domain.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Booking> findByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.show s
        LEFT JOIN FETCH s.refundPolicy
        WHERE b.id = :id AND b.user.id = :userId
        """)
    Optional<Booking> findByIdAndUserIdForCancel(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    List<Booking> findByShowId(Long showId);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.user
        JOIN FETCH b.show
        WHERE b.show.id = :showId
        """)
    List<Booking> findByShowIdWithUser(@Param("showId") Long showId);
}
