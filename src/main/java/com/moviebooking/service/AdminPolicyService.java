package com.moviebooking.service;

import com.moviebooking.domain.entity.AppConfig;
import com.moviebooking.domain.entity.DiscountCode;
import com.moviebooking.domain.entity.RefundPolicy;
import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.DiscountCodeRepository;
import com.moviebooking.repository.RefundPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPolicyService {

    private final PricingService pricingService;
    private final DiscountCodeRepository discountCodeRepository;
    private final RefundPolicyRepository refundPolicyRepository;

    public AdminDtos.PricingConfigResponse getPricing() {
        var config = pricingService.getPricingConfig();
        var r = new AdminDtos.PricingConfigResponse();
        r.setRegularPrice(config.getRegularPrice());
        r.setPremiumPrice(config.getPremiumPrice());
        r.setWeekendMultiplier(config.getWeekendMultiplier());
        return r;
    }

    @Transactional
    public AdminDtos.PricingConfigResponse updatePricing(AdminDtos.PricingConfigRequest request) {
        var config = new AppConfig.PricingConfig(
                request.getRegularPrice(), request.getPremiumPrice(), request.getWeekendMultiplier());
        var updated = pricingService.updatePricingConfig(config);
        var r = new AdminDtos.PricingConfigResponse();
        r.setRegularPrice(updated.getRegularPrice());
        r.setPremiumPrice(updated.getPremiumPrice());
        r.setWeekendMultiplier(updated.getWeekendMultiplier());
        return r;
    }

    @Transactional
    public AdminDtos.DiscountCodeResponse createDiscount(AdminDtos.DiscountCodeRequest request) {
        if (discountCodeRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new AppException("CODE_EXISTS", "Discount code already exists", HttpStatus.BAD_REQUEST);
        }
        var entity = discountCodeRepository.save(DiscountCode.builder()
                .code(request.getCode().toUpperCase())
                .type(request.getType())
                .value(request.getValue())
                .maxUses(request.getMaxUses())
                .validUntil(request.getValidUntil())
                .active(request.getActive())
                .build());
        return toDiscountResponse(entity);
    }

    public List<AdminDtos.DiscountCodeResponse> listDiscounts() {
        return discountCodeRepository.findAll().stream().map(this::toDiscountResponse).toList();
    }

    @Transactional
    public AdminDtos.RefundPolicyResponse createRefundPolicy(AdminDtos.RefundPolicyRequest request) {
        var rules = request.getRules().stream()
                .map(r -> new RefundPolicy.RefundRule(r.getHoursBeforeShow(), r.getRefundPercent()))
                .collect(Collectors.toList());
        var entity = refundPolicyRepository.save(RefundPolicy.builder()
                .name(request.getName())
                .rules(rules)
                .build());
        return toRefundResponse(entity);
    }

    public List<AdminDtos.RefundPolicyResponse> listRefundPolicies() {
        return refundPolicyRepository.findAll().stream().map(this::toRefundResponse).toList();
    }

    private AdminDtos.DiscountCodeResponse toDiscountResponse(DiscountCode d) {
        var r = new AdminDtos.DiscountCodeResponse();
        r.setId(d.getId());
        r.setCode(d.getCode());
        r.setType(d.getType());
        r.setValue(d.getValue());
        r.setMaxUses(d.getMaxUses());
        r.setUsesCount(d.getUsesCount());
        r.setValidUntil(d.getValidUntil());
        r.setActive(d.getActive());
        return r;
    }

    private AdminDtos.RefundPolicyResponse toRefundResponse(RefundPolicy p) {
        var r = new AdminDtos.RefundPolicyResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setRules(p.getRules());
        return r;
    }
}
