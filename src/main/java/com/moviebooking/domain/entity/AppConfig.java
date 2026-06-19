package com.moviebooking.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppConfig {

    @Id
    @Column(name = "config_key")
    private String configKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private PricingConfig value;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingConfig {
        private BigDecimal regularPrice;
        private BigDecimal premiumPrice;
        private BigDecimal weekendMultiplier;
    }
}
