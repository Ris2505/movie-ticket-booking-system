package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.BookingSeatStatus;
import com.moviebooking.domain.enums.BookingStatus;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.BookingSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancelBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private BookingSeatRepository bookingSeatRepository;

    /** PLAN #14 */
    @Test
    void partialCancelPartiallyCancelledWithRefund() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        long policyId = createRefundPolicy(admin);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 3, policyId,
                Instant.now().plus(3, ChronoUnit.DAYS));
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-14", null);
        long bookingId = booking.get("id").asLong();

        var cancelReq = new BookingDtos.CancelBookingRequest();
        cancelReq.setSeatIds(List.of(fixture.seatIds().get(0), fixture.seatIds().get(1)));
        var cancelResp = postCancel(customer, bookingId, cancelReq);

        assertThat(cancelResp.get("status").asText()).isEqualTo("PARTIALLY_CANCELLED");
        assertThat(cancelResp.get("totalRefund").decimalValue()).isEqualByComparingTo("400.00");
        assertThat(bookingSeatRepository.findByBookingIdAndStatus(bookingId, BookingSeatStatus.ACTIVE).size())
                .isEqualTo(1);
    }

    /** PLAN #15 */
    @Test
    void fullCancelReleasesAllSeats() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        long policyId = createRefundPolicy(admin);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 2, policyId,
                Instant.now().plus(3, ChronoUnit.DAYS));
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-15", null);
        long bookingId = booking.get("id").asLong();

        var cancelReq = new BookingDtos.CancelBookingRequest();
        cancelReq.setSeatIds(fixture.seatIds());
        var cancelResp = postCancel(customer, bookingId, cancelReq);

        assertThat(cancelResp.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    /** PLAN #16 */
    @Test
    void cancelAfterShowStartZeroRefund() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        long policyId = createRefundPolicy(admin);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1, policyId,
                Instant.now().minus(1, ChronoUnit.HOURS));
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-16", null);

        var cancelReq = new BookingDtos.CancelBookingRequest();
        cancelReq.setSeatIds(fixture.seatIds());
        var cancelResp = postCancel(customer, booking.get("id").asLong(), cancelReq);
        assertThat(cancelResp.get("totalRefund").decimalValue()).isEqualByComparingTo("0.00");
    }

    /** PLAN #17 */
    @Test
    void customerCannotReadOthersBooking() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var owner = login(mockMvc, objectMapper, uniqueEmail("owner"), "password123", UserRole.CUSTOMER);
        var other = login(mockMvc, objectMapper, uniqueEmail("other"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, owner, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, owner, hold.get("id").asLong(),
                "token_success", "idem-17", null);
        long bookingId = booking.get("id").asLong();

        var error = postError(mockMvc, objectMapper, other, "/bookings/" + bookingId, null,
                HttpMethod.GET, HttpStatus.NOT_FOUND);
        assertThat(error.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    private long createRefundPolicy(org.springframework.mock.web.MockHttpSession admin) throws Exception {
        var policyReq = new AdminDtos.RefundPolicyRequest();
        policyReq.setName("Standard");
        var rule = new AdminDtos.RefundRuleRequest();
        rule.setHoursBeforeShow(48);
        rule.setRefundPercent(100);
        policyReq.setRules(List.of(rule));
        return extractId(postJson(mockMvc, objectMapper, admin, "/admin/refund-policies", policyReq, HttpStatus.CREATED));
    }

    private com.fasterxml.jackson.databind.JsonNode postCancel(
            org.springframework.mock.web.MockHttpSession customer, long bookingId, BookingDtos.CancelBookingRequest cancelReq) throws Exception {
        var result = mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .session(customer)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
