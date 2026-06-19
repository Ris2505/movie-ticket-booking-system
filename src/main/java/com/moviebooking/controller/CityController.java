package com.moviebooking.controller;

import com.moviebooking.dto.catalog.CatalogDtos;
import com.moviebooking.service.CatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Cities")
public class CityController {

    private final CatalogService catalogService;

    @GetMapping("/cities")
    public List<CatalogDtos.IdNameResponse> listCities() {
        return catalogService.listCities();
    }

    @GetMapping("/cities/{cityId}/theaters")
    public List<CatalogDtos.TheaterResponse> listTheaters(@PathVariable Long cityId) {
        return catalogService.listTheatersByCity(cityId);
    }

    @PostMapping("/admin/cities")
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogDtos.IdNameResponse createCity(@RequestBody CatalogDtos.NameRequest request) {
        return catalogService.createCity(request);
    }
}
