package com.moviebooking.controller;

import com.moviebooking.dto.catalog.CatalogDtos;
import com.moviebooking.service.CatalogService;
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
@Tag(name = "Admin Catalog")
public class AdminCatalogController {

    private final CatalogService catalogService;

    @PostMapping("/cities/{cityId}/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.TheaterResponse createTheater(@PathVariable Long cityId,
                                                     @Valid @RequestBody CatalogDtos.NameRequest request) {
        return catalogService.createTheater(cityId, request);
    }

    @PostMapping("/theaters/{theaterId}/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ScreenResponse createScreen(@PathVariable Long theaterId,
                                                     @Valid @RequestBody CatalogDtos.NameRequest request) {
        return catalogService.createScreen(theaterId, request);
    }

    @GetMapping("/screens/{screenId}/seats")
    public List<CatalogDtos.SeatResponse> listSeats(@PathVariable Long screenId) {
        return catalogService.listSeats(screenId);
    }

    @PostMapping("/screens/{screenId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CatalogDtos.SeatResponse> bulkCreateSeats(@PathVariable Long screenId,
                                                          @Valid @RequestBody CatalogDtos.BulkSeatRequest request) {
        return catalogService.bulkCreateSeats(screenId, request);
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.MovieResponse createMovie(@Valid @RequestBody CatalogDtos.MovieRequest request) {
        return catalogService.createMovie(request);
    }

    @GetMapping("/movies")
    public List<CatalogDtos.MovieResponse> listMovies() {
        return catalogService.listMovies();
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ShowResponse createShow(@Valid @RequestBody CatalogDtos.ShowRequest request) {
        return catalogService.createShow(request);
    }
}
