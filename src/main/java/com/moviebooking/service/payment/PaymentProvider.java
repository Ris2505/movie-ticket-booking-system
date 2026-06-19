package com.moviebooking.service.payment;

import com.moviebooking.domain.enums.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentProvider {
    PaymentMethod method();
    PaymentResult charge(BigDecimal amount, String token);
}
