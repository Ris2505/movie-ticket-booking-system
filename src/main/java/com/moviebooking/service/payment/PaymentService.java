package com.moviebooking.service.payment;

import com.moviebooking.domain.enums.PaymentMethod;
import com.moviebooking.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final Map<PaymentMethod, PaymentProvider> providers;

    public PaymentService(List<PaymentProvider> providerList) {
        this.providers = new EnumMap<>(PaymentMethod.class);
        for (PaymentProvider provider : providerList) {
            providers.put(provider.method(), provider);
        }
    }

    public PaymentResult charge(PaymentMethod method, BigDecimal amount, String token) {
        PaymentProvider provider = providers.get(method);
        if (provider == null) {
            throw new AppException("UNSUPPORTED_PAYMENT", "Payment method not supported", HttpStatus.BAD_REQUEST);
        }
        return provider.charge(amount, token);
    }
}
