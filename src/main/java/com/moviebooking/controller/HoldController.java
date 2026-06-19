package com.moviebooking.controller;

import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.security.SecurityUtils;
import com.moviebooking.service.HoldService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Holds")
public class HoldController {

    private final HoldService holdService;
    private final SecurityUtils securityUtils;

    @PostMapping("/shows/{showId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDtos.HoldResponse createHold(@PathVariable Long showId,
                                               @Valid @RequestBody BookingDtos.HoldRequest request) {
        return holdService.createHold(showId, securityUtils.currentUserId(), request);
    }

    @DeleteMapping("/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseHold(@PathVariable Long holdId) {
        holdService.releaseHold(holdId, securityUtils.currentUserId());
    }
}
