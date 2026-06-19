package com.moviebooking.service;

import com.moviebooking.domain.entity.AppConfig;
import com.moviebooking.domain.enums.SeatTier;
import com.moviebooking.repository.AppConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private AppConfigRepository appConfigRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(appConfigRepository, "Asia/Kolkata");
        var config = new AppConfig.PricingConfig(
                new BigDecimal("200"), new BigDecimal("350"), new BigDecimal("1.25"));
        when(appConfigRepository.findById("pricing")).thenReturn(Optional.of(
                AppConfig.builder().configKey("pricing").value(config).build()));
    }

    @Test
    void regularWeekdayPrice() {
        Instant monday = ist(2026, 6, 15, 18);
        assertThat(pricingService.computeBasePrice(SeatTier.REGULAR, monday))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void regularWeekendPrice() {
        Instant saturday = ist(2026, 6, 20, 18);
        assertThat(pricingService.computeBasePrice(SeatTier.REGULAR, saturday))
                .isEqualByComparingTo("250.00");
    }

    @Test
    void premiumWeekdayPrice() {
        Instant wednesday = ist(2026, 6, 17, 18);
        assertThat(pricingService.computeBasePrice(SeatTier.PREMIUM, wednesday))
                .isEqualByComparingTo("350.00");
    }

    @Test
    void premiumWeekendPrice() {
        Instant saturday = ist(2026, 6, 20, 18);
        assertThat(pricingService.computeBasePrice(SeatTier.PREMIUM, saturday))
                .isEqualByComparingTo("437.50");
    }

    @Test
    void fridayLateEveningIsStillWeekdayInIst() {
        Instant friday2359 = ist(2026, 6, 19, 23, 59);
        assertThat(pricingService.isWeekend(friday2359)).isFalse();
        assertThat(pricingService.computeBasePrice(SeatTier.REGULAR, friday2359))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void saturdayMidnightIsWeekendInIst() {
        Instant saturdayMidnight = ist(2026, 6, 20, 0, 0);
        assertThat(pricingService.isWeekend(saturdayMidnight)).isTrue();
        assertThat(pricingService.computeBasePrice(SeatTier.REGULAR, saturdayMidnight))
                .isEqualByComparingTo("250.00");
    }

    private static Instant ist(int year, int month, int day, int hour) {
        return ist(year, month, day, hour, 0);
    }

    private static Instant ist(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant();
    }
}
