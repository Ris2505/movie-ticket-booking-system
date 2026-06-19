package com.moviebooking.config;

import com.moviebooking.domain.enums.PaymentMethod;
import com.moviebooking.service.payment.MockPaymentProvider;
import com.moviebooking.service.payment.PaymentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    PaymentProvider cardPaymentProvider() {
        return new MockPaymentProvider(PaymentMethod.CARD);
    }

    @Bean
    PaymentProvider upiPaymentProvider() {
        return new MockPaymentProvider(PaymentMethod.UPI);
    }

    @Bean
    PaymentProvider walletPaymentProvider() {
        return new MockPaymentProvider(PaymentMethod.WALLET);
    }
}
