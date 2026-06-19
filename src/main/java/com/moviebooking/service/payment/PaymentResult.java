package com.moviebooking.service.payment;

import com.moviebooking.domain.enums.PaymentMethod;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentResult {
    boolean success;
    String providerRef;
    String failureReason;
}
