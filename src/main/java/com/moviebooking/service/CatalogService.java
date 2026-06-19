package com.moviebooking.service;

import com.moviebooking.domain.entity.*;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.domain.enums.ShowStatus;
import com.moviebooking.dto.catalog.CatalogDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CityRepository cityRepository;
    private final TheaterRepository theaterRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final RefundPolicyRepository refundPolicyRepository;
    private final PricingService pricingService;

    public List<CatalogDtos.IdNameResponse> listCities() {
        return cityRepository.findAll().stream()
                .map(c -> CatalogDtos.IdNameResponse.of(c.getId(), c.getName()))
                .toList();
    }

    @Transactional
    public CatalogDtos.IdNameResponse createCity(CatalogDtos.NameRequest request) {
        if (cityRepository.findByName(request.getName()).isPresent()) {
            throw new AppException("CITY_EXISTS", "City already exists", HttpStatus.BAD_REQUEST);
        }
        var city = cityRepository.save(City.builder().name(request.getName()).build());
        return CatalogDtos.IdNameResponse.of(city.getId(), city.getName());
    }

    public List<CatalogDtos.TheaterResponse> listTheatersByCity(Long cityId) {
        requireCity(cityId);
        return theaterRepository.findByCityId(cityId).stream().map(this::toTheaterResponse).toList();
    }

    @Transactional
    public CatalogDtos.TheaterResponse createTheater(Long cityId, CatalogDtos.NameRequest request) {
        var city = requireCity(cityId);
        var theater = theaterRepository.save(Theater.builder().city(city).name(request.getName()).build());
        return toTheaterResponse(theater);
    }

    public List<CatalogDtos.ScreenResponse> listScreens(Long theaterId) {
        requireTheater(theaterId);
        return screenRepository.findByTheaterId(theaterId).stream().map(this::toScreenResponse).toList();
    }

    @Transactional
    public CatalogDtos.ScreenResponse createScreen(Long theaterId, CatalogDtos.NameRequest request) {
        var theater = requireTheater(theaterId);
        var screen = screenRepository.save(Screen.builder().theater(theater).name(request.getName()).build());
        return toScreenResponse(screen);
    }

    public List<CatalogDtos.SeatResponse> listSeats(Long screenId) {
        requireScreen(screenId);
        return seatRepository.findByScreenId(screenId).stream().map(this::toSeatResponse).toList();
    }

    @Transactional
    public List<CatalogDtos.SeatResponse> bulkCreateSeats(Long screenId, CatalogDtos.BulkSeatRequest request) {
        var screen = requireScreen(screenId);
        var responses = new ArrayList<CatalogDtos.SeatResponse>();
        for (var item : request.getSeats()) {
            var seat = seatRepository.save(Seat.builder()
                    .screen(screen)
                    .rowLabel(item.getRowLabel())
                    .seatNumber(item.getSeatNumber())
                    .tier(item.getTier())
                    .build());
            responses.add(toSeatResponse(seat));
        }
        return responses;
    }

    @Transactional
    public CatalogDtos.MovieResponse createMovie(CatalogDtos.MovieRequest request) {
        var movie = movieRepository.save(Movie.builder()
                .title(request.getTitle())
                .durationMinutes(request.getDurationMinutes())
                .build());
        return toMovieResponse(movie);
    }

    public List<CatalogDtos.MovieResponse> listMovies() {
        return movieRepository.findAll().stream().map(this::toMovieResponse).toList();
    }

    @Transactional
    public CatalogDtos.ShowResponse createShow(CatalogDtos.ShowRequest request) {
        var movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> notFound("Movie not found"));
        var screen = requireScreen(request.getScreenId());
        RefundPolicy refundPolicy = null;
        if (request.getRefundPolicyId() != null) {
            refundPolicy = refundPolicyRepository.findById(request.getRefundPolicyId())
                    .orElseThrow(() -> notFound("Refund policy not found"));
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException("INVALID_SHOW_TIME", "End time must be after start time", HttpStatus.BAD_REQUEST);
        }

        var show = showRepository.save(Show.builder()
                .movie(movie)
                .screen(screen)
                .refundPolicy(refundPolicy)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(ShowStatus.SCHEDULED)
                .build());

        var seats = seatRepository.findByScreenId(screen.getId());
        for (Seat seat : seats) {
            showSeatRepository.save(ShowSeat.builder()
                    .show(show)
                    .seat(seat)
                    .status(ShowSeatStatus.AVAILABLE)
                    .build());
        }
        return toShowResponse(show);
    }

    @Transactional
    public CatalogDtos.ShowResponse cancelShow(Long showId) {
        var show = requireShow(showId);
        if (show.getStatus() == ShowStatus.CANCELLED) {
            throw new AppException("SHOW_CANCELLED", "Show already cancelled", HttpStatus.CONFLICT);
        }
        show.setStatus(ShowStatus.CANCELLED);
        showRepository.save(show);
        return toShowResponse(show);
    }

    private City requireCity(Long cityId) {
        return cityRepository.findById(cityId).orElseThrow(() -> notFound("City not found"));
    }

    private Theater requireTheater(Long theaterId) {
        return theaterRepository.findById(theaterId).orElseThrow(() -> notFound("Theater not found"));
    }

    private Screen requireScreen(Long screenId) {
        return screenRepository.findById(screenId).orElseThrow(() -> notFound("Screen not found"));
    }

    private Show requireShow(Long showId) {
        return showRepository.findById(showId).orElseThrow(() -> notFound("Show not found"));
    }

    private AppException notFound(String message) {
        return new AppException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    private CatalogDtos.TheaterResponse toTheaterResponse(Theater t) {
        var r = new CatalogDtos.TheaterResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setCityId(t.getCity().getId());
        return r;
    }

    private CatalogDtos.ScreenResponse toScreenResponse(Screen s) {
        var r = new CatalogDtos.ScreenResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setTheaterId(s.getTheater().getId());
        return r;
    }

    private CatalogDtos.SeatResponse toSeatResponse(Seat s) {
        var r = new CatalogDtos.SeatResponse();
        r.setId(s.getId());
        r.setRowLabel(s.getRowLabel());
        r.setSeatNumber(s.getSeatNumber());
        r.setTier(s.getTier());
        return r;
    }

    private CatalogDtos.MovieResponse toMovieResponse(Movie m) {
        var r = new CatalogDtos.MovieResponse();
        r.setId(m.getId());
        r.setTitle(m.getTitle());
        r.setDurationMinutes(m.getDurationMinutes());
        return r;
    }

    public CatalogDtos.ShowResponse toShowResponse(Show show) {
        var r = new CatalogDtos.ShowResponse();
        r.setId(show.getId());
        r.setMovieId(show.getMovie().getId());
        r.setMovieTitle(show.getMovie().getTitle());
        r.setScreenId(show.getScreen().getId());
        r.setScreenName(show.getScreen().getName());
        r.setTheaterName(show.getScreen().getTheater().getName());
        r.setCityName(show.getScreen().getTheater().getCity().getName());
        r.setStartTime(show.getStartTime());
        r.setEndTime(show.getEndTime());
        r.setStatus(show.getStatus());
        r.setRefundPolicyId(show.getRefundPolicy() != null ? show.getRefundPolicy().getId() : null);
        return r;
    }
}
