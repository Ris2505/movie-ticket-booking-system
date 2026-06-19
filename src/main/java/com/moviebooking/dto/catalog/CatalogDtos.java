package com.moviebooking.dto.catalog;

import com.moviebooking.domain.enums.SeatTier;
import com.moviebooking.domain.enums.ShowStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public class CatalogDtos {

    @Data
    public static class NameRequest {
        @NotBlank
        private String name;
    }

    @Data
    public static class IdNameResponse {
        private Long id;
        private String name;

        public static IdNameResponse of(Long id, String name) {
            var r = new IdNameResponse();
            r.id = id;
            r.name = name;
            return r;
        }
    }

    @Data
    public static class TheaterResponse {
        private Long id;
        private String name;
        private Long cityId;
    }

    @Data
    public static class ScreenResponse {
        private Long id;
        private String name;
        private Long theaterId;
    }

    @Data
    public static class SeatLayoutItem {
        @NotBlank
        private String rowLabel;
        @NotNull @Min(1)
        private Integer seatNumber;
        @NotNull
        private SeatTier tier;
    }

    @Data
    public static class BulkSeatRequest {
        @NotEmpty
        @Valid
        private List<SeatLayoutItem> seats;
    }

    @Data
    public static class SeatResponse {
        private Long id;
        private String rowLabel;
        private Integer seatNumber;
        private SeatTier tier;
    }

    @Data
    public static class MovieRequest {
        @NotBlank
        private String title;
        @NotNull @Min(1)
        private Integer durationMinutes;
    }

    @Data
    public static class MovieResponse {
        private Long id;
        private String title;
        private Integer durationMinutes;
    }

    @Data
    public static class ShowRequest {
        @NotNull
        private Long movieId;
        @NotNull
        private Long screenId;
        private Long refundPolicyId;
        @NotNull
        private Instant startTime;
        @NotNull
        private Instant endTime;
    }

    @Data
    public static class ShowResponse {
        private Long id;
        private Long movieId;
        private String movieTitle;
        private Long screenId;
        private String screenName;
        private String theaterName;
        private String cityName;
        private Instant startTime;
        private Instant endTime;
        private ShowStatus status;
        private Long refundPolicyId;
    }
}
