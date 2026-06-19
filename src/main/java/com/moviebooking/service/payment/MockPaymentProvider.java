package com.moviebooking.service.payment;

import com.moviebooking.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public class MockPaymentProvider implements PaymentProvider {

    public static final String TOKEN_SUCCESS = "token_success";
    public static final String TOKEN_FAIL = "token_fail";

    private final PaymentMethod method;

    public MockPaymentProvider(PaymentMethod method) {
        this.method = method;
    }

    @Override
    public PaymentMethod method() {
        return method;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String token) {
        if (TOKEN_FAIL.equals(token)) {
            return PaymentResult.builder()
                    .success(false)
                    .failureReason("Mock payment declined")
                    .build();
        }
        if (!TOKEN_SUCCESS.equals(token)) {
            return PaymentResult.builder()
                    .success(false)
                    .failureReason("Invalid payment token")
                    .build();
        }
        return PaymentResult.builder()
                .success(true)
                .providerRef(method.name() + "-" + UUID.randomUUID())
                .build();
    }
}
