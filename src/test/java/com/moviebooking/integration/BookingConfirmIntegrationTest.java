package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.NotificationStatus;
import com.moviebooking.domain.enums.NotificationType;
import com.moviebooking.domain.enums.PaymentStatus;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.HoldRepository;
import com.moviebooking.repository.NotificationRepository;
import com.moviebooking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BookingConfirmIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private HoldRepository holdRepository;
    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    /** PLAN #7 */
    @Test
    void confirmWithSuccessTokenBooksSeats() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-7", null);

        assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(booking.get("paymentStatus").asText()).isEqualTo("SUCCESS");
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss ->
                assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.BOOKED));
    }

    /** PLAN #8 */
    @Test
    void confirmWithFailTokenLeavesSeatsHeldAndNoBooking() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        long holdId = hold.get("id").asLong();
        long before = bookingRepository.count();

        var bookReq = objectMapper.createObjectNode()
                .put("holdId", holdId)
                .put("paymentMethod", "CARD")
                .put("paymentToken", "token_fail");
        var error = postError(mockMvc, objectMapper, customer, "/bookings", bookReq,
                HttpMethod.POST, HttpStatus.PAYMENT_REQUIRED);
        assertThat(error.get("code").asText()).isEqualTo("PAYMENT_FAILED");
        assertThat(bookingRepository.count()).isEqualTo(before);
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss ->
                assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.HELD));
    }

    /** PLAN #9 */
    @Test
    void idempotentConfirmCreatesSingleBooking() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        long holdId = hold.get("id").asLong();
        String key = "idem-9-" + fixture.showId();

        var first = confirmBooking(mockMvc, objectMapper, customer, holdId, "token_success", key, null);
        var second = confirmBooking(mockMvc, objectMapper, customer, holdId, "token_success", key, null);

        assertThat(first.get("id").asLong()).isEqualTo(second.get("id").asLong());
        assertThat(bookingRepository.findByIdempotencyKey(key)).isPresent();
        assertThat(bookingRepository.findAll().stream()
                .filter(b -> key.equals(b.getIdempotencyKey())).count()).isEqualTo(1);
    }

    /** PLAN #10 */
    @Test
    void confirmExpiredHoldRejected() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        long holdId = hold.get("id").asLong();

        showSeatRepository.findByHoldId(holdId).forEach(ss -> {
            ss.setHeldUntil(Instant.now().minusSeconds(120));
            showSeatRepository.save(ss);
        });
        var holdEntity = holdRepository.findById(holdId).orElseThrow();
        holdEntity.setExpiresAt(Instant.now().minusSeconds(120));
        holdRepository.save(holdEntity);

        var bookReq = objectMapper.createObjectNode()
                .put("holdId", holdId)
                .put("paymentMethod", "CARD")
                .put("paymentToken", "token_success");
        var error = postError(mockMvc, objectMapper, customer, "/bookings", bookReq,
                HttpMethod.POST, HttpStatus.CONFLICT);
        assertThat(error.get("code").asText()).isEqualTo("HOLD_EXPIRED");
    }

    /** PLAN #11 */
    @Test
    void confirmWithValidDiscount() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 2);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);

        var discountReq = new AdminDtos.DiscountCodeRequest();
        discountReq.setCode("SAVE10_" + fixture.showId());
        discountReq.setType(com.moviebooking.domain.enums.DiscountType.PERCENT);
        discountReq.setValue(new BigDecimal("10"));
        discountReq.setActive(true);
        postJson(mockMvc, objectMapper, admin, "/admin/discount-codes", discountReq, HttpStatus.CREATED);

        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-11", "SAVE10_" + fixture.showId());

        assertThat(booking.get("discountAmount").decimalValue()).isEqualByComparingTo("55.00");
        assertThat(booking.get("subtotal").decimalValue()).isEqualByComparingTo("550.00");
        assertThat(booking.get("totalAmount").decimalValue()).isEqualByComparingTo("495.00");
    }

    /** PLAN #12 */
    @Test
    void confirmWithInvalidDiscountRejected() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var bookReq = objectMapper.createObjectNode()
                .put("holdId", hold.get("id").asLong())
                .put("paymentMethod", "CARD")
                .put("paymentToken", "token_success")
                .put("discountCode", "NOPE");
        var error = postError(mockMvc, objectMapper, customer, "/bookings", bookReq,
                HttpMethod.POST, HttpStatus.BAD_REQUEST);
        assertThat(error.get("code").asText()).isEqualTo("INVALID_DISCOUNT");
    }

    /** PLAN #19 */
    @Test
    void confirmPersistsSentNotification() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-19", null);
        long bookingId = booking.get("id").asLong();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.existsByBookingIdAndType(bookingId, NotificationType.BOOKING_CONFIRMATION))
                    .isTrue();
        });
    }
}
