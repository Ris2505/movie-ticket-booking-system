package com.moviebooking.service;

import com.moviebooking.domain.entity.Show;
import com.moviebooking.domain.enums.ShowStatus;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.dto.catalog.CatalogDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ShowBrowseService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final CatalogService catalogService;
    private final ZoneId zoneId;

    public ShowBrowseService(ShowRepository showRepository,
                             ShowSeatRepository showSeatRepository,
                             CatalogService catalogService,
                             @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.catalogService = catalogService;
        this.zoneId = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ShowResponse> listShows(Long cityId, Long movieId, LocalDate date) {
        Instant dateStart = null;
        Instant dateEnd = null;
        if (date != null) {
            dateStart = date.atStartOfDay(zoneId).toInstant();
            dateEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        }
        return showRepository.findShows(
                ShowStatus.SCHEDULED,
                cityId == null, cityId != null ? cityId : 0L,
                movieId == null, movieId != null ? movieId : 0L,
                dateStart == null, dateStart,
                dateEnd == null, dateEnd)
                .stream()
                .map(catalogService::toShowResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ShowResponse getShow(Long showId) {
        var show = showRepository.findById(showId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Show not found", HttpStatus.NOT_FOUND));
        return catalogService.toShowResponse(show);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.ShowSeatMapResponse> getSeatMap(Long showId) {
        requireScheduledShow(showId);
        return showSeatRepository.findByShowId(showId).stream().map(ss -> {
            var r = new BookingDtos.ShowSeatMapResponse();
            r.setShowSeatId(ss.getId());
            r.setSeatId(ss.getSeat().getId());
            r.setRowLabel(ss.getSeat().getRowLabel());
            r.setSeatNumber(ss.getSeat().getSeatNumber());
            r.setTier(ss.getSeat().getTier());
            r.setStatus(ss.getStatus());
            r.setLockedBasePrice(ss.getLockedBasePrice());
            return r;
        }).toList();
    }

    private Show requireScheduledShow(Long showId) {
        var show = showRepository.findById(showId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "Show not found", HttpStatus.NOT_FOUND));
        if (show.getStatus() != ShowStatus.SCHEDULED) {
            throw new AppException("SHOW_CANCELLED", "Show is not available", HttpStatus.CONFLICT);
        }
        return show;
    }
}
