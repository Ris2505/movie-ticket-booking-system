package com.moviebooking.service;

import com.moviebooking.domain.entity.DiscountCode;
import com.moviebooking.domain.enums.DiscountType;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.DiscountCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    private DiscountService discountService;

    @BeforeEach
    void setUp() {
        discountService = new DiscountService(discountCodeRepository);
    }

    @Test
    void blankCodeReturnsNull() {
        assertThat(discountService.validateAndGet(null)).isNull();
        assertThat(discountService.validateAndGet("  ")).isNull();
    }

    @Test
    void validPercentDiscount() {
        var code = activeCode("SAVE10", DiscountType.PERCENT, new BigDecimal("10"));
        when(discountCodeRepository.findByCodeIgnoreCase("save10")).thenReturn(Optional.of(code));

        var result = discountService.validateAndGet("save10");
        assertThat(result.getCode()).isEqualTo("SAVE10");
        assertThat(discountService.computeDiscountAmount(result, new BigDecimal("400")))
                .isEqualByComparingTo("40.00");
    }

    @Test
    void validFlatDiscountCappedAtSubtotal() {
        var code = activeCode("FLAT50", DiscountType.FLAT, new BigDecimal("50"));
        assertThat(discountService.computeDiscountAmount(code, new BigDecimal("30")))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void expiredCodeRejected() {
        var code = activeCode("OLD", DiscountType.PERCENT, new BigDecimal("10"));
        code.setValidUntil(Instant.now().minus(1, ChronoUnit.HOURS));
        when(discountCodeRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> discountService.validateAndGet("OLD"))
                .isInstanceOf(AppException.class)
                .extracting("code", "status")
                .containsExactly("INVALID_DISCOUNT", HttpStatus.BAD_REQUEST);
    }

    @Test
    void inactiveCodeRejected() {
        var code = activeCode("OFF", DiscountType.PERCENT, new BigDecimal("10"));
        code.setActive(false);
        when(discountCodeRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> discountService.validateAndGet("OFF"))
                .isInstanceOf(AppException.class)
                .extracting("code")
                .isEqualTo("INVALID_DISCOUNT");
    }

    @Test
    void maxUsesExceededRejected() {
        var code = activeCode("LIMIT", DiscountType.PERCENT, new BigDecimal("10"));
        code.setMaxUses(2);
        code.setUsesCount(2);
        when(discountCodeRepository.findByCodeIgnoreCase("LIMIT")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> discountService.validateAndGet("LIMIT"))
                .isInstanceOf(AppException.class)
                .extracting("code")
                .isEqualTo("INVALID_DISCOUNT");
    }

    @Test
    void unknownCodeRejected() {
        when(discountCodeRepository.findByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> discountService.validateAndGet("MISSING"))
                .isInstanceOf(AppException.class)
                .extracting("code")
                .isEqualTo("INVALID_DISCOUNT");
    }

    private static DiscountCode activeCode(String code, DiscountType type, BigDecimal value) {
        return DiscountCode.builder()
                .code(code)
                .type(type)
                .value(value)
                .active(true)
                .usesCount(0)
                .build();
    }
}
