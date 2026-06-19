package com.moviebooking.service;

import com.moviebooking.domain.entity.DiscountCode;
import com.moviebooking.domain.enums.DiscountType;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountCodeRepository;

    public DiscountCode validateAndGet(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        var discount = discountCodeRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException("INVALID_DISCOUNT", "Discount code not found", HttpStatus.BAD_REQUEST));

        if (!Boolean.TRUE.equals(discount.getActive())) {
            throw new AppException("INVALID_DISCOUNT", "Discount code is inactive", HttpStatus.BAD_REQUEST);
        }
        if (discount.getValidUntil() != null && discount.getValidUntil().isBefore(Instant.now())) {
            throw new AppException("INVALID_DISCOUNT", "Discount code expired", HttpStatus.BAD_REQUEST);
        }
        if (discount.getMaxUses() != null && discount.getUsesCount() >= discount.getMaxUses()) {
            throw new AppException("INVALID_DISCOUNT", "Discount code usage limit reached", HttpStatus.BAD_REQUEST);
        }
        return discount;
    }

    public BigDecimal computeDiscountAmount(DiscountCode discount, BigDecimal subtotal) {
        if (discount.getType() == DiscountType.PERCENT) {
            return subtotal.multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return discount.getValue().min(subtotal);
    }

    public void incrementUsage(DiscountCode discount) {
        discount.setUsesCount(discount.getUsesCount() + 1);
        discountCodeRepository.save(discount);
    }
}
