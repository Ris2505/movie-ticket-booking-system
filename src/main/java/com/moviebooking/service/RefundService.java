package com.moviebooking.service;

import com.moviebooking.domain.entity.RefundPolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

@Service
public class RefundService {

    public BigDecimal computeRefund(BigDecimal pricePaid, Instant showStartTime, Instant cancelTime,
                                    RefundPolicy policy) {
        if (cancelTime.isAfter(showStartTime) || cancelTime.equals(showStartTime)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (policy == null || policy.getRules() == null || policy.getRules().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long hoursBefore = Duration.between(cancelTime, showStartTime).toHours();
        int refundPercent = policy.getRules().stream()
                .filter(r -> hoursBefore >= r.getHoursBeforeShow())
                .max(java.util.Comparator.comparingInt(RefundPolicy.RefundRule::getHoursBeforeShow))
                .map(RefundPolicy.RefundRule::getRefundPercent)
                .orElse(0);

        return pricePaid.multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
