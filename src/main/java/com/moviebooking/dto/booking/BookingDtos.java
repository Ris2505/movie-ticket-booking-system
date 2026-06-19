package com.moviebooking.dto.booking;

import com.moviebooking.domain.enums.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BookingDtos {

    @Data
    public static class HoldRequest {
        @NotEmpty
        private List<Long> seatIds;
    }

    @Data
    public static class HoldResponse {
        private Long id;
        private Long showId;
        private Instant expiresAt;
        private HoldStatus status;
        private List<HeldSeatResponse> seats;
    }

    @Data
    public static class HeldSeatResponse {
        private Long showSeatId;
        private Long seatId;
        private String rowLabel;
        private Integer seatNumber;
        private SeatTier tier;
        private BigDecimal lockedBasePrice;
    }

    @Data
    public static class ConfirmBookingRequest {
        @NotNull
        private Long holdId;
        @NotNull
        private PaymentMethod paymentMethod;
        @NotBlank
        private String paymentToken;
        private String discountCode;
    }

    @Data
    public static class BookingResponse {
        private Long id;
        private Long showId;
        private BookingStatus status;
        private BigDecimal subtotal;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
        private PaymentStatus paymentStatus;
        private String paymentMethod;
        private Instant createdAt;
        private List<BookingSeatResponse> seats;
    }

    @Data
    public static class BookingSeatResponse {
        private Long seatId;
        private String rowLabel;
        private Integer seatNumber;
        private BigDecimal pricePaid;
        private BigDecimal refundAmount;
        private BookingSeatStatus status;
    }

    @Data
    public static class CancelBookingRequest {
        @NotEmpty
        private List<Long> seatIds;
    }

    @Data
    public static class CancelBookingResponse {
        private Long bookingId;
        private BookingStatus status;
        private BigDecimal totalRefund;
        private List<BookingSeatResponse> seats;
    }

    @Data
    public static class ShowSeatMapResponse {
        private Long showSeatId;
        private Long seatId;
        private String rowLabel;
        private Integer seatNumber;
        private SeatTier tier;
        private ShowSeatStatus status;
        private BigDecimal lockedBasePrice;
    }
}
