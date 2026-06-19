package com.moviebooking.service.payment;

import com.moviebooking.domain.enums.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(List.of(
                new MockPaymentProvider(PaymentMethod.CARD),
                new MockPaymentProvider(PaymentMethod.UPI),
                new MockPaymentProvider(PaymentMethod.WALLET)));
    }

    @Test
    void successToken() {
        var result = paymentService.charge(PaymentMethod.CARD, new BigDecimal("500"), MockPaymentProvider.TOKEN_SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).startsWith("CARD-");
    }

    @Test
    void failToken() {
        var result = paymentService.charge(PaymentMethod.UPI, new BigDecimal("500"), MockPaymentProvider.TOKEN_FAIL);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("Mock payment declined");
    }

    @Test
    void invalidToken() {
        var result = paymentService.charge(PaymentMethod.WALLET, new BigDecimal("500"), "bad_token");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("Invalid payment token");
    }
}
