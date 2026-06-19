package com.moviebooking.controller;

import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.dto.catalog.CatalogDtos;
import com.moviebooking.service.ShowBrowseService;
import com.moviebooking.service.ShowManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
@Tag(name = "Shows")
public class ShowController {

    private final ShowBrowseService showBrowseService;
    private final ShowManagementService showManagementService;

    @GetMapping
    public List<CatalogDtos.ShowResponse> listShows(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showBrowseService.listShows(cityId, movieId, date);
    }

    @GetMapping("/{showId}")
    public CatalogDtos.ShowResponse getShow(@PathVariable Long showId) {
        return showBrowseService.getShow(showId);
    }

    @GetMapping("/{showId}/seats")
    public List<BookingDtos.ShowSeatMapResponse> getSeatMap(@PathVariable Long showId) {
        return showBrowseService.getSeatMap(showId);
    }

    @PatchMapping("/{showId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelShow(@PathVariable Long showId) {
        showManagementService.cancelShow(showId);
    }
}
