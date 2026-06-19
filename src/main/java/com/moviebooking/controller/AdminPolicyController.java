package com.moviebooking.controller;

import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.service.AdminPolicyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Policies")
public class AdminPolicyController {

    private final AdminPolicyService adminPolicyService;

    @GetMapping("/pricing")
    public AdminDtos.PricingConfigResponse getPricing() {
        return adminPolicyService.getPricing();
    }

    @PutMapping("/pricing")
    public AdminDtos.PricingConfigResponse updatePricing(@Valid @RequestBody AdminDtos.PricingConfigRequest request) {
        return adminPolicyService.updatePricing(request);
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.DiscountCodeResponse createDiscount(@Valid @RequestBody AdminDtos.DiscountCodeRequest request) {
        return adminPolicyService.createDiscount(request);
    }

    @GetMapping("/discount-codes")
    public List<AdminDtos.DiscountCodeResponse> listDiscounts() {
        return adminPolicyService.listDiscounts();
    }

    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.RefundPolicyResponse createRefundPolicy(@Valid @RequestBody AdminDtos.RefundPolicyRequest request) {
        return adminPolicyService.createRefundPolicy(request);
    }

    @GetMapping("/refund-policies")
    public List<AdminDtos.RefundPolicyResponse> listRefundPolicies() {
        return adminPolicyService.listRefundPolicies();
    }
}
