package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.HoldStatus;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.repository.HoldRepository;
import com.moviebooking.repository.ShowSeatRepository;
import com.moviebooking.service.HoldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class HoldIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShowSeatRepository showSeatRepository;
    @Autowired
    private HoldRepository holdRepository;
    @Autowired
    private HoldService holdService;

    /** PLAN #3 */
    @Test
    void holdTwoSeatsLocksPricesAndSetsActiveHold() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 2);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());

        assertThat(hold.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(hold.get("seats")).hasSize(2);
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss -> {
            assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.HELD);
            assertThat(ss.getLockedBasePrice()).isNotNull();
        });
    }

    /** PLAN #5 */
    @Test
    void holdAllOrNothingWhenOneSeatAlreadyHeld() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 2);
        var user1 = login(mockMvc, objectMapper, uniqueEmail("u1"), "password123", UserRole.CUSTOMER);
        var user2 = login(mockMvc, objectMapper, uniqueEmail("u2"), "password123", UserRole.CUSTOMER);

        holdSeats(mockMvc, objectMapper, user1, fixture.showId(), List.of(fixture.seatIds().get(1)));
        var holdReq = objectMapper.createObjectNode();
        holdReq.putArray("seatIds").add(fixture.seatIds().get(0)).add(fixture.seatIds().get(1));
        var error = postError(mockMvc, objectMapper, user2, "/shows/" + fixture.showId() + "/holds", holdReq,
                HttpMethod.POST, HttpStatus.CONFLICT);
        assertThat(error.get("code").asText()).isEqualTo("SEAT_UNAVAILABLE");

        var seat0 = showSeatRepository.findByShowIdAndSeatId(fixture.showId(), fixture.seatIds().get(0)).orElseThrow();
        assertThat(seat0.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE);
    }

    /** PLAN #6 */
    @Test
    void releaseHoldFreesSeats() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        long holdId = hold.get("id").asLong();

        postError(mockMvc, objectMapper, customer, "/holds/" + holdId, null, HttpMethod.DELETE, HttpStatus.NO_CONTENT);
        assertThat(holdRepository.findById(holdId).orElseThrow().getStatus()).isEqualTo(HoldStatus.RELEASED);
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss ->
                assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
    }

    /** PLAN #13 */
    @Test
    void holdExpiryJobReleasesSeats() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var hold = holdSeats(mockMvc, objectMapper, customer, fixture.showId(), fixture.seatIds());
        long holdId = hold.get("id").asLong();

        showSeatRepository.findByHoldId(holdId).forEach(ss -> {
            ss.setHeldUntil(Instant.now().minusSeconds(60));
            showSeatRepository.save(ss);
        });
        var holdEntity = holdRepository.findById(holdId).orElseThrow();
        holdEntity.setExpiresAt(Instant.now().minusSeconds(60));
        holdRepository.save(holdEntity);

        assertThat(holdService.expireHeldSeats()).isEqualTo(1);
        assertThat(holdRepository.findById(holdId).orElseThrow().getStatus()).isEqualTo(HoldStatus.EXPIRED);
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss ->
                assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
    }
}
