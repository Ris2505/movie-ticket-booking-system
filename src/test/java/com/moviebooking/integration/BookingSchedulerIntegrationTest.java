package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.NotificationType;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.repository.NotificationRepository;
import com.moviebooking.scheduler.BookingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BookingSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingScheduler bookingScheduler;
    @Autowired
    private NotificationRepository notificationRepository;

    /** PLAN #20 */
    @Test
    void reminderJobCreatesNotificationForUpcomingShow() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        long policyId = createRefundPolicy(admin);
        Instant showStart = Instant.now().plus(2, ChronoUnit.HOURS);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1, policyId, showStart);

        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        var booking = confirmBooking(mockMvc, objectMapper, customer, hold.get("id").asLong(),
                "token_success", "idem-20", null);
        long bookingId = booking.get("id").asLong();

        bookingScheduler.sendReminders();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.existsByBookingIdAndType(bookingId, NotificationType.SHOW_REMINDER))
                        .isTrue());
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
}
