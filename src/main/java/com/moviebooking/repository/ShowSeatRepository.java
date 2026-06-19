package com.moviebooking.repository;

import com.moviebooking.domain.entity.ShowSeat;
import com.moviebooking.domain.enums.ShowSeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowId(Long showId);

    List<ShowSeat> findByHoldId(Long holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId AND ss.seat.id IN :seatIds ORDER BY ss.id")
    List<ShowSeat> findByShowIdAndSeatIdsForUpdate(
            @Param("showId") Long showId,
            @Param("seatIds") List<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.hold.id = :holdId ORDER BY ss.id")
    List<ShowSeat> findByHoldIdForUpdate(@Param("holdId") Long holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT ss FROM ShowSeat ss
        WHERE ss.status = 'HELD' AND ss.heldUntil < :now
        """)
    List<ShowSeat> findExpiredHeldSeats(@Param("now") Instant now);

    Optional<ShowSeat> findByShowIdAndSeatId(Long showId, Long seatId);

    long countByShowId(Long showId);
}
