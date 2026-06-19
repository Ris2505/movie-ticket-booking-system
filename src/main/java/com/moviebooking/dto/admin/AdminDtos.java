package com.moviebooking.dto.admin;

import com.moviebooking.domain.entity.RefundPolicy;
import com.moviebooking.domain.enums.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AdminDtos {

    @Data
    public static class PricingConfigRequest {
        @NotNull @DecimalMin("0.01")
        private BigDecimal regularPrice;
        @NotNull @DecimalMin("0.01")
        private BigDecimal premiumPrice;
        @NotNull @DecimalMin("1.0")
        private BigDecimal weekendMultiplier;
    }

    @Data
    public static class PricingConfigResponse {
        private BigDecimal regularPrice;
        private BigDecimal premiumPrice;
        private BigDecimal weekendMultiplier;
    }

    @Data
    public static class DiscountCodeRequest {
        @NotBlank
        private String code;
        @NotNull
        private DiscountType type;
        @NotNull @DecimalMin("0.01")
        private BigDecimal value;
        private Integer maxUses;
        private Instant validUntil;
        @NotNull
        private Boolean active;
    }

    @Data
    public static class DiscountCodeResponse {
        private Long id;
        private String code;
        private DiscountType type;
        private BigDecimal value;
        private Integer maxUses;
        private Integer usesCount;
        private Instant validUntil;
        private Boolean active;
    }

    @Data
    public static class RefundRuleRequest {
        @Min(0)
        private int hoursBeforeShow;
        @Min(0) @Max(100)
        private int refundPercent;
    }

    @Data
    public static class RefundPolicyRequest {
        @NotBlank
        private String name;
        @NotEmpty @Valid
        private List<RefundRuleRequest> rules;
    }

    @Data
    public static class RefundPolicyResponse {
        private Long id;
        private String name;
        private List<RefundPolicy.RefundRule> rules;
    }
}
