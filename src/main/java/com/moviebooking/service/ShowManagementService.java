package com.moviebooking.service;

import com.moviebooking.domain.enums.BookingSeatStatus;
import com.moviebooking.domain.enums.BookingStatus;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import com.moviebooking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShowManagementService {

    private final CatalogService catalogService;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final NotificationService notificationService;

    @Transactional
    public void cancelShow(Long showId) {
        catalogService.cancelShow(showId);
        var bookings = bookingRepository.findByShowId(showId);
        for (var booking : bookings) {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }
            var seats = bookingSeatRepository.findByBookingId(booking.getId());
            for (var bs : seats) {
                if (bs.getStatus() == BookingSeatStatus.ACTIVE) {
                    bs.setStatus(BookingSeatStatus.CANCELLED);
                    bookingSeatRepository.save(bs);
                    var ss = bs.getShowSeat();
                    if (ss.getStatus() == ShowSeatStatus.BOOKED) {
                        HoldService.releaseSeat(ss);
                        ss.setBooking(null);
                        showSeatRepository.save(ss);
                    }
                }
            }
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            notificationService.sendShowCancelled(booking.getUser(), booking);
        }
        showSeatRepository.findByShowId(showId).forEach(ss -> {
            if (ss.getStatus() != ShowSeatStatus.BOOKED) {
                HoldService.releaseSeat(ss);
                showSeatRepository.save(ss);
            }
        });
    }
}
