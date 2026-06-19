package com.moviebooking.service;

import com.moviebooking.config.AppProperties;
import com.moviebooking.domain.entity.Show;
import com.moviebooking.domain.entity.User;
import com.moviebooking.domain.enums.ShowStatus;
import com.moviebooking.dto.booking.BookingDtos;
import com.moviebooking.exception.AppException;
import com.moviebooking.repository.HoldRepository;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @Mock private ShowRepository showRepository;
    @Mock private ShowSeatRepository showSeatRepository;
    @Mock private HoldRepository holdRepository;
    @Mock private UserRepository userRepository;
    @Mock private PricingService pricingService;
    @Mock private AppProperties appProperties;

    @InjectMocks
    private HoldService holdService;

    @Test
    void emptySeatListRejected() {
        var request = new BookingDtos.HoldRequest();
        request.setSeatIds(List.of());

        assertThatThrownBy(() -> holdService.createHold(1L, 1L, request))
                .isInstanceOf(AppException.class)
                .extracting("code", "status")
                .containsExactly("INVALID_SEATS", HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelledShowRejected() {
        var show = Show.builder().id(1L).status(ShowStatus.CANCELLED).build();
        when(showRepository.findById(1L)).thenReturn(Optional.of(show));

        var request = new BookingDtos.HoldRequest();
        request.setSeatIds(List.of(10L));

        assertThatThrownBy(() -> holdService.createHold(1L, 1L, request))
                .isInstanceOf(AppException.class)
                .extracting("code", "status")
                .containsExactly("SHOW_CANCELLED", HttpStatus.CONFLICT);
    }

    @Test
    void unknownUserRejected() {
        var show = Show.builder().id(1L).status(ShowStatus.SCHEDULED).build();
        when(showRepository.findById(1L)).thenReturn(Optional.of(show));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var request = new BookingDtos.HoldRequest();
        request.setSeatIds(List.of(10L));

        assertThatThrownBy(() -> holdService.createHold(1L, 99L, request))
                .isInstanceOf(AppException.class)
                .extracting("code", "status")
                .containsExactly("NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
