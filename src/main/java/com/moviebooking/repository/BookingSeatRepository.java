package com.moviebooking.repository;

import com.moviebooking.domain.entity.BookingSeat;
import com.moviebooking.domain.enums.BookingSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @org.springframework.data.jpa.repository.Query("""
        SELECT bs FROM BookingSeat bs
        JOIN FETCH bs.seat
        JOIN FETCH bs.showSeat
        WHERE bs.booking.id = :bookingId
        """)
    List<BookingSeat> findByBookingIdWithDetails(Long bookingId);

    List<BookingSeat> findByBookingId(Long bookingId);
    List<BookingSeat> findByBookingIdAndStatus(Long bookingId, BookingSeatStatus status);
}
