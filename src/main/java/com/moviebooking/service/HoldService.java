package com.moviebooking.service;

import com.moviebooking.config.AppProperties;
import com.moviebooking.domain.entity.Hold;
import com.moviebooking.domain.entity.Show;
import com.moviebooking.domain.entity.ShowSeat;
import com.moviebooking.domain.enums.HoldStatus;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.domain.enums.ShowStatus;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.HoldRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final HoldRepository holdRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final AppProperties appProperties;

    @Transactional
    public BookingDtos.HoldResponse createHold(Long showId, Long userId, BookingDtos.HoldRequest request) {
        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new AppException("INVALID_SEATS", "Seat list cannot be empty", HttpStatus.BAD_REQUEST);
        }

        var show = showRepository.findById(showId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Show not found", HttpStatus.NOT_FOUND));
        if (show.getStatus() != ShowStatus.SCHEDULED) {
            throw new AppException("SHOW_CANCELLED", "Show is not available for booking", HttpStatus.CONFLICT);
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

        List<ShowSeat> showSeats = showSeatRepository.findByShowIdAndSeatIdsForUpdate(showId, request.getSeatIds());
        if (showSeats.size() != request.getSeatIds().size()) {
            throw new AppException("SEAT_NOT_FOUND", "One or more seats not found for this show", HttpStatus.BAD_REQUEST);
        }

        for (ShowSeat ss : showSeats) {
            if (ss.getStatus() != ShowSeatStatus.AVAILABLE) {
                throw new AppException("SEAT_UNAVAILABLE", "One or more seats are not available", HttpStatus.CONFLICT);
            }
        }

        Instant expiresAt = Instant.now().plusSeconds(appProperties.getHoldDurationMinutes() * 60L);
        var hold = holdRepository.save(Hold.builder()
                .user(user)
                .show(show)
                .expiresAt(expiresAt)
                .status(HoldStatus.ACTIVE)
                .build());

        for (ShowSeat ss : showSeats) {
            BigDecimal price = pricingService.computeBasePrice(ss.getSeat().getTier(), show.getStartTime());
            ss.setStatus(ShowSeatStatus.HELD);
            ss.setHold(hold);
            ss.setHeldUntil(expiresAt);
            ss.setLockedBasePrice(price);
            ss.setBooking(null);
        }
        showSeatRepository.saveAll(showSeats);

        return toHoldResponse(hold, showSeats);
    }

    @Transactional
    public void releaseHold(Long holdId, Long userId) {
        var hold = holdRepository.findByIdAndUserId(holdId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Hold not found", HttpStatus.NOT_FOUND));
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new AppException("HOLD_INVALID", "Hold is not active", HttpStatus.CONFLICT);
        }

        List<ShowSeat> seats = showSeatRepository.findByHoldIdForUpdate(holdId);
        for (ShowSeat ss : seats) {
            releaseSeat(ss);
        }
        showSeatRepository.saveAll(seats);
        hold.setStatus(HoldStatus.RELEASED);
        holdRepository.save(hold);
    }

    @Transactional
    public int expireHeldSeats() {
        List<ShowSeat> expired = showSeatRepository.findExpiredHeldSeats(Instant.now());
        int count = 0;
        for (ShowSeat ss : expired) {
            if (ss.getHold() != null && ss.getHold().getStatus() == HoldStatus.ACTIVE) {
                ss.getHold().setStatus(HoldStatus.EXPIRED);
                holdRepository.save(ss.getHold());
            }
            releaseSeat(ss);
            count++;
        }
        if (!expired.isEmpty()) {
            showSeatRepository.saveAll(expired);
        }
        return count;
    }

    static void releaseSeat(ShowSeat ss) {
        ss.setStatus(ShowSeatStatus.AVAILABLE);
        ss.setHold(null);
        ss.setHeldUntil(null);
        ss.setLockedBasePrice(null);
    }

    public BookingDtos.HoldResponse toHoldResponse(Hold hold, List<ShowSeat> seats) {
        var r = new BookingDtos.HoldResponse();
        r.setId(hold.getId());
        r.setShowId(hold.getShow().getId());
        r.setExpiresAt(hold.getExpiresAt());
        r.setStatus(hold.getStatus());
        r.setSeats(seats.stream().map(ss -> {
            var s = new BookingDtos.HeldSeatResponse();
            s.setShowSeatId(ss.getId());
            s.setSeatId(ss.getSeat().getId());
            s.setRowLabel(ss.getSeat().getRowLabel());
            s.setSeatNumber(ss.getSeat().getSeatNumber());
            s.setTier(ss.getSeat().getTier());
            s.setLockedBasePrice(ss.getLockedBasePrice());
            return s;
        }).toList());
        return r;
    }
}
