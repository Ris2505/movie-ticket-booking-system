package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.repository.UserRepository;
import com.moviebooking.service.HoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.function.LongFunction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class HoldConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HoldService holdService;
    @Autowired
    private UserRepository userRepository;

    /** PLAN #4 — service-level concurrent hold (DB pessimistic lock) */
    @Test
    void concurrentHoldSameSeatExactlyOneSucceeds() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        long seatId = fixture.seatIds().get(0);

        String email1 = uniqueEmail("u1");
        String email2 = uniqueEmail("u2");
        login(mockMvc, objectMapper, email1, "password123", UserRole.CUSTOMER);
        login(mockMvc, objectMapper, email2, "password123", UserRole.CUSTOMER);
        long userId1 = userRepository.findByEmail(email1).orElseThrow().getId();
        long userId2 = userRepository.findByEmail(email2).orElseThrow().getId();

        var success = new AtomicInteger(0);
        var conflict = new AtomicInteger(0);
        var latch = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        LongFunction<Runnable> attempt = (long userId) -> () -> {
            try {
                var req = new BookingDtos.HoldRequest();
                req.setSeatIds(List.of(seatId));
                holdService.createHold(fixture.showId(), userId, req);
                success.incrementAndGet();
            } catch (Exception e) {
                conflict.incrementAndGet();
            } finally {
                latch.countDown();
            }
        };

        pool.submit(attempt.apply(userId1));
        pool.submit(attempt.apply(userId2));
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
    }
}
