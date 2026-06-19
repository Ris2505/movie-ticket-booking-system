package com.moviebooking.controller;

import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.security.SecurityUtils;
import com.moviebooking.service.BookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDtos.BookingResponse confirm(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingDtos.ConfirmBookingRequest request) {
        return bookingService.confirm(securityUtils.currentUserId(), idempotencyKey, request);
    }

    @GetMapping
    public List<BookingDtos.BookingResponse> listBookings() {
        return bookingService.listBookings(securityUtils.currentUserId());
    }

    @GetMapping("/{bookingId}")
    public BookingDtos.BookingResponse getBooking(@PathVariable Long bookingId) {
        return bookingService.getBooking(securityUtils.currentUserId(), bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingDtos.CancelBookingResponse cancel(@PathVariable Long bookingId,
                                                    @Valid @RequestBody BookingDtos.CancelBookingRequest request) {
        return bookingService.cancel(securityUtils.currentUserId(), bookingId, request);
    }
}
