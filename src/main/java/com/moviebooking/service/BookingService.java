package com.moviebooking.service;

import com.moviebooking.domain.entity.*;
import com.moviebooking.domain.enums.*;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.*;
import com.moviebooking.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final HoldRepository holdRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final NotificationService notificationService;

    @Transactional
    public BookingDtos.BookingResponse confirm(Long userId, String idempotencyKey, BookingDtos.ConfirmBookingRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                var booking = existing.get();
                if (!booking.getUser().getId().equals(userId)) {
                    throw new AppException("NOT_FOUND", "Booking not found", HttpStatus.NOT_FOUND);
                }
                return toResponse(booking);
            }
        }

        var hold = holdRepository.findByIdAndUserId(request.getHoldId(), userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Hold not found", HttpStatus.NOT_FOUND));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new AppException("HOLD_INVALID", "Hold is not active", HttpStatus.CONFLICT);
        }
        if (hold.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException("HOLD_EXPIRED", "Hold has expired", HttpStatus.CONFLICT);
        }

        List<ShowSeat> seats = showSeatRepository.findByHoldIdForUpdate(hold.getId());
        if (seats.isEmpty()) {
            throw new AppException("HOLD_INVALID", "Hold has no seats", HttpStatus.CONFLICT);
        }

        for (ShowSeat ss : seats) {
            if (ss.getStatus() != ShowSeatStatus.HELD || ss.getHold() == null
                    || !Objects.equals(ss.getHold().getId(), hold.getId())) {
                throw new AppException("SEAT_UNAVAILABLE", "Seats no longer held", HttpStatus.CONFLICT);
            }
        }

        BigDecimal subtotal = seats.stream()
                .map(ShowSeat::getLockedBasePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DiscountCode discountCode = discountService.validateAndGet(request.getDiscountCode());
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountCode != null) {
            discountAmount = discountService.computeDiscountAmount(discountCode, subtotal);
        }
        BigDecimal total = subtotal.subtract(discountAmount);

        var paymentResult = paymentService.charge(request.getPaymentMethod(), total, request.getPaymentToken());
        if (!paymentResult.isSuccess()) {
            throw new AppException("PAYMENT_FAILED", paymentResult.getFailureReason(), HttpStatus.PAYMENT_REQUIRED);
        }

        var user = userRepository.getReferenceById(userId);
        var booking = bookingRepository.save(Booking.builder()
                .user(user)
                .show(hold.getShow())
                .hold(hold)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .totalAmount(total)
                .discountCode(discountCode)
                .paymentMethod(request.getPaymentMethod().name())
                .paymentStatus(PaymentStatus.SUCCESS)
                .providerRef(paymentResult.getProviderRef())
                .idempotencyKey(idempotencyKey)
                .status(BookingStatus.CONFIRMED)
                .build());

        for (ShowSeat ss : seats) {
            ss.setStatus(ShowSeatStatus.BOOKED);
            ss.setBooking(booking);
            ss.setHold(null);
            ss.setHeldUntil(null);
            bookingSeatRepository.save(BookingSeat.builder()
                    .booking(booking)
                    .showSeat(ss)
                    .seat(ss.getSeat())
                    .pricePaid(ss.getLockedBasePrice())
                    .status(BookingSeatStatus.ACTIVE)
                    .build());
        }
        showSeatRepository.saveAll(seats);

        hold.setStatus(HoldStatus.CONSUMED);
        holdRepository.save(hold);

        if (discountCode != null) {
            discountService.incrementUsage(discountCode);
        }

        notificationService.sendBookingConfirmation(user, booking);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingResponse> listBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingResponse getBooking(Long userId, Long bookingId) {
        var booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Booking not found", HttpStatus.NOT_FOUND));
        return toResponse(booking);
    }

    @Transactional
    public BookingDtos.CancelBookingResponse cancel(Long userId, Long bookingId, BookingDtos.CancelBookingRequest request) {
        var booking = bookingRepository.findByIdAndUserIdForCancel(bookingId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Booking not found", HttpStatus.NOT_FOUND));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new AppException("BOOKING_CANCELLED", "Booking already cancelled", HttpStatus.CONFLICT);
        }

        var bookingSeats = bookingSeatRepository.findByBookingIdWithDetails(bookingId);
        var toCancel = bookingSeats.stream()
                .filter(bs -> bs.getStatus() == BookingSeatStatus.ACTIVE)
                .filter(bs -> request.getSeatIds().contains(bs.getSeat().getId()))
                .toList();

        if (toCancel.isEmpty()) {
            throw new AppException("INVALID_SEATS", "No active seats to cancel", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        RefundPolicy policy = booking.getShow().getRefundPolicy();
        BigDecimal totalRefund = BigDecimal.ZERO;

        for (BookingSeat bs : toCancel) {
            BigDecimal refund = refundService.computeRefund(
                    bs.getPricePaid(), booking.getShow().getStartTime(), now, policy);
            bs.setRefundAmount(refund);
            bs.setStatus(BookingSeatStatus.CANCELLED);
            totalRefund = totalRefund.add(refund);

            ShowSeat ss = bs.getShowSeat();
            HoldService.releaseSeat(ss);
            ss.setBooking(null);
            showSeatRepository.save(ss);
        }
        bookingSeatRepository.saveAll(toCancel);

        long activeCount = bookingSeatRepository.findByBookingIdAndStatus(bookingId, BookingSeatStatus.ACTIVE).size();

        if (activeCount == 0) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setPaymentStatus(totalRefund.signum() > 0 ? PaymentStatus.REFUNDED : booking.getPaymentStatus());
        } else {
            booking.setStatus(BookingStatus.PARTIALLY_CANCELLED);
            booking.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        bookingRepository.save(booking);

        notificationService.sendBookingCancelled(booking.getUser(), booking, java.util.Map.of(
                "totalRefund", totalRefund,
                "cancelledSeatIds", request.getSeatIds()
        ));

        var response = new BookingDtos.CancelBookingResponse();
        response.setBookingId(booking.getId());
        response.setStatus(booking.getStatus());
        response.setTotalRefund(totalRefund);
        response.setSeats(bookingSeatRepository.findByBookingId(bookingId).stream().map(this::toSeatResponse).toList());
        return response;
    }

    private BookingDtos.BookingResponse toResponse(Booking booking) {
        var r = new BookingDtos.BookingResponse();
        r.setId(booking.getId());
        r.setShowId(booking.getShow().getId());
        r.setStatus(booking.getStatus());
        r.setSubtotal(booking.getSubtotal());
        r.setDiscountAmount(booking.getDiscountAmount());
        r.setTotalAmount(booking.getTotalAmount());
        r.setPaymentStatus(booking.getPaymentStatus());
        r.setPaymentMethod(booking.getPaymentMethod());
        r.setCreatedAt(booking.getCreatedAt());
        r.setSeats(bookingSeatRepository.findByBookingId(booking.getId()).stream()
                .map(this::toSeatResponse).toList());
        return r;
    }

    private BookingDtos.BookingSeatResponse toSeatResponse(BookingSeat bs) {
        var r = new BookingDtos.BookingSeatResponse();
        r.setSeatId(bs.getSeat().getId());
        r.setRowLabel(bs.getSeat().getRowLabel());
        r.setSeatNumber(bs.getSeat().getSeatNumber());
        r.setPricePaid(bs.getPricePaid());
        r.setRefundAmount(bs.getRefundAmount());
        r.setStatus(bs.getStatus());
        return r;
    }
}
