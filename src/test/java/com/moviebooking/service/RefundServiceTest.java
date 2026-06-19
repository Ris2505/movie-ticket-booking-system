package com.moviebooking.service;

import com.moviebooking.domain.entity.RefundPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefundServiceTest {

    private final RefundService refundService = new RefundService();

    private final RefundPolicy tieredPolicy = RefundPolicy.builder()
            .rules(List.of(
                    new RefundPolicy.RefundRule(48, 100),
                    new RefundPolicy.RefundRule(24, 50),
                    new RefundPolicy.RefundRule(0, 0)))
            .build();

    @Test
    void fullRefundWhenFarFromShow() {
        Instant showStart = Instant.now().plus(72, ChronoUnit.HOURS);
        assertThat(refundService.computeRefund(new BigDecimal("200"), showStart, Instant.now(), tieredPolicy))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void halfRefundWithin24Hours() {
        Instant showStart = Instant.now().plus(30, ChronoUnit.HOURS);
        assertThat(refundService.computeRefund(new BigDecimal("200"), showStart, Instant.now(), tieredPolicy))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void zeroRefundAfterShowStart() {
        Instant showStart = Instant.now().minus(1, ChronoUnit.HOURS);
        assertThat(refundService.computeRefund(new BigDecimal("200"), showStart, Instant.now(), tieredPolicy))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void zeroRefundWhenNoPolicy() {
        Instant showStart = Instant.now().plus(72, ChronoUnit.HOURS);
        assertThat(refundService.computeRefund(new BigDecimal("150"), showStart, Instant.now(), null))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void partialSeatRefundUsesSeatPrice() {
        Instant showStart = Instant.now().plus(72, ChronoUnit.HOURS);
        assertThat(refundService.computeRefund(new BigDecimal("350"), showStart, Instant.now(), tieredPolicy))
                .isEqualByComparingTo("350.00");
    }
}
