package com.moviebooking.service;

import com.moviebooking.domain.entity.AppConfig;
import com.moviebooking.domain.enums.SeatTier;
import com.moviebooking.repository.AppConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class PricingService {

    private static final String PRICING_KEY = "pricing";

    private final AppConfigRepository appConfigRepository;
    private final ZoneId zoneId;

    public PricingService(AppConfigRepository appConfigRepository,
                          @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.appConfigRepository = appConfigRepository;
        this.zoneId = ZoneId.of(timezone);
    }

    public BigDecimal computeBasePrice(SeatTier tier, Instant showStartTime) {
        var config = getPricingConfig();
        BigDecimal base = tier == SeatTier.PREMIUM ? config.getPremiumPrice() : config.getRegularPrice();
        if (isWeekend(showStartTime)) {
            base = base.multiply(config.getWeekendMultiplier()).setScale(2, RoundingMode.HALF_UP);
        }
        return base;
    }

    public AppConfig.PricingConfig getPricingConfig() {
        return appConfigRepository.findById(PRICING_KEY)
                .map(AppConfig::getValue)
                .orElseThrow(() -> new IllegalStateException("Pricing config missing"));
    }

    public AppConfig.PricingConfig updatePricingConfig(AppConfig.PricingConfig config) {
        var entity = appConfigRepository.findById(PRICING_KEY)
                .orElse(AppConfig.builder().configKey(PRICING_KEY).build());
        entity.setValue(config);
        return appConfigRepository.save(entity).getValue();
    }

    public boolean isWeekend(Instant showStartTime) {
        ZonedDateTime zdt = showStartTime.atZone(zoneId);
        DayOfWeek day = zdt.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
