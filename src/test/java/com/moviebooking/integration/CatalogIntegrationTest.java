package com.moviebooking.integration;

import com.moviebooking.AbstractIntegrationTest;
import com.moviebooking.domain.enums.ShowSeatStatus;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.admin.AdminDtos;
import com.moviebooking.repository.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static com.moviebooking.support.IntegrationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShowSeatRepository showSeatRepository;

    /** PLAN #1 */
    @Test
    void adminCreatesCatalogShowMaterializesAvailableSeats() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 3);

        assertThat(showSeatRepository.countByShowId(fixture.showId())).isEqualTo(3);
        showSeatRepository.findByShowId(fixture.showId()).forEach(ss ->
                assertThat(ss.getStatus()).isEqualTo(ShowSeatStatus.AVAILABLE));
    }

    /** PLAN #2 */
    @Test
    void customerBrowsesShowsByCityAndDate() throws Exception {
        var admin = login(mockMvc, objectMapper, uniqueEmail("admin"), "password123", UserRole.ADMIN);
        var fixture = createShowWithSeats(mockMvc, objectMapper, admin, 1);
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var date = showLocalDate(fixture.showStart());
        var shows = getList(mockMvc, customer, objectMapper,
                "/shows?cityId=" + fixture.cityId() + "&date=" + date);
        assertThat(shows).anyMatch(n -> n.get("id").asLong() == fixture.showId());
    }

    /** PLAN #18 */
    @Test
    void customerCannotCallAdminApi() throws Exception {
        var customer = login(mockMvc, objectMapper, uniqueEmail("cust"), "password123", UserRole.CUSTOMER);
        var body = new AdminDtos.DiscountCodeRequest();
        body.setCode("X");
        body.setType(com.moviebooking.domain.enums.DiscountType.PERCENT);
        body.setValue(new java.math.BigDecimal("10"));
        body.setActive(true);
        postError(mockMvc, objectMapper, customer, "/admin/discount-codes", body,
                HttpMethod.POST, HttpStatus.FORBIDDEN);
    }
}
