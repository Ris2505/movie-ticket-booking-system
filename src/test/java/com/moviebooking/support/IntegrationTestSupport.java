package com.moviebooking.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.domain.enums.SeatTier;
import com.moviebooking.domain.enums.UserRole;
import com.moviebooking.dto.auth.AuthDtos;
import com.moviebooking.dto.catalog.CatalogDtos;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class IntegrationTestSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private IntegrationTestSupport() {}

    public record ShowFixture(
            long cityId, long theaterId, long screenId, long movieId,
            long showId, List<Long> seatIds, Instant showStart) {}

    public static String uniqueEmail(String prefix) {
        return prefix + COUNTER.incrementAndGet() + "@test.com";
    }

    public static MockHttpSession login(MockMvc mockMvc, ObjectMapper objectMapper,
                                    String email, String password, UserRole role) throws Exception {
        var reg = new AuthDtos.RegisterRequest();
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setRole(role);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        var loginReq = new AuthDtos.LoginRequest();
        loginReq.setEmail(email);
        loginReq.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public static ShowFixture createShowWithSeats(
            MockMvc mockMvc, ObjectMapper objectMapper, MockHttpSession adminSession, int seatCount) throws Exception {
        return createShowWithSeats(mockMvc, objectMapper, adminSession, seatCount, null,
                Instant.now().plus(3, ChronoUnit.DAYS));
    }

    public static ShowFixture createShowWithSeats(
            MockMvc mockMvc, ObjectMapper objectMapper, MockHttpSession adminSession,
            int seatCount, Long refundPolicyId, Instant showStart) throws Exception {

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long cityId = extractId(postJson(mockMvc, objectMapper, adminSession, "/admin/cities",
                nameBody("City-" + suffix), HttpStatus.OK));
        long theaterId = extractId(postJson(mockMvc, objectMapper, adminSession,
                "/admin/cities/" + cityId + "/theaters", nameBody("Theater-" + suffix), HttpStatus.CREATED));
        long screenId = extractId(postJson(mockMvc, objectMapper, adminSession,
                "/admin/theaters/" + theaterId + "/screens", nameBody("Screen-" + suffix), HttpStatus.CREATED));

        var seatReq = new CatalogDtos.BulkSeatRequest();
        var seats = new ArrayList<CatalogDtos.SeatLayoutItem>();
        for (int i = 1; i <= seatCount; i++) {
            var seat = new CatalogDtos.SeatLayoutItem();
            seat.setRowLabel("A");
            seat.setSeatNumber(i);
            seat.setTier(i == seatCount ? SeatTier.PREMIUM : SeatTier.REGULAR);
            seats.add(seat);
        }
        seatReq.setSeats(seats);
        var seatResponseNode = postJson(mockMvc, objectMapper, adminSession,
                "/admin/screens/" + screenId + "/seats", seatReq, HttpStatus.CREATED);
        var seatIds = new ArrayList<Long>();
        seatResponseNode.forEach(n -> seatIds.add(n.get("id").asLong()));

        var movieReq = new CatalogDtos.MovieRequest();
        movieReq.setTitle("Movie-" + suffix);
        movieReq.setDurationMinutes(120);
        long movieId = extractId(postJson(mockMvc, objectMapper, adminSession, "/admin/movies", movieReq, HttpStatus.CREATED));

        var showReq = new CatalogDtos.ShowRequest();
        showReq.setMovieId(movieId);
        showReq.setScreenId(screenId);
        showReq.setRefundPolicyId(refundPolicyId);
        showReq.setStartTime(showStart);
        showReq.setEndTime(showStart.plus(120, ChronoUnit.MINUTES));
        long showId = extractId(postJson(mockMvc, objectMapper, adminSession, "/admin/shows", showReq, HttpStatus.CREATED));

        return new ShowFixture(cityId, theaterId, screenId, movieId, showId, seatIds, showStart);
    }

    public static JsonNode holdSeats(MockMvc mockMvc, ObjectMapper objectMapper,
                                     MockHttpSession customerSession, long showId, List<Long> seatIds) throws Exception {
        var holdReq = objectMapper.createObjectNode();
        var arr = holdReq.putArray("seatIds");
        seatIds.forEach(arr::add);
        return postJson(mockMvc, objectMapper, customerSession, "/shows/" + showId + "/holds", holdReq, HttpStatus.CREATED);
    }

    public static JsonNode confirmBooking(MockMvc mockMvc, ObjectMapper objectMapper,
                                          MockHttpSession customerSession, long holdId, String paymentToken,
                                          String idempotencyKey, String discountCode) throws Exception {
        var bookReq = objectMapper.createObjectNode()
                .put("holdId", holdId)
                .put("paymentMethod", "CARD")
                .put("paymentToken", paymentToken);
        if (discountCode != null) {
            bookReq.put("discountCode", discountCode);
        }
        var builder = post("/bookings")
                .session(customerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bookReq));
        if (idempotencyKey != null) {
            builder = builder.header("Idempotency-Key", idempotencyKey);
        }
        var result = mockMvc.perform(builder).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    public static JsonNode postError(MockMvc mockMvc, ObjectMapper objectMapper, MockHttpSession session,
                                     String path, Object body, org.springframework.http.HttpMethod method,
                                     HttpStatus expected) throws Exception {
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(method, path).session(session);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        }
        var result = mockMvc.perform(builder).andExpect(status().is(expected.value())).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? null : objectMapper.readTree(content);
    }

    public static List<JsonNode> getList(MockMvc mockMvc, MockHttpSession session, ObjectMapper objectMapper,
                                         String path) throws Exception {
        var result = mockMvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        var list = new ArrayList<JsonNode>();
        tree.forEach(list::add);
        return list;
    }

    public static LocalDate showLocalDate(Instant showStart) {
        return showStart.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
    }

    public static long extractId(JsonNode node) {
        return node.get("id").asLong();
    }

    private static CatalogDtos.NameRequest nameBody(String name) {
        var r = new CatalogDtos.NameRequest();
        r.setName(name);
        return r;
    }

    public static JsonNode postJson(MockMvc mockMvc, ObjectMapper objectMapper, MockHttpSession session,
                                    String path, Object body, HttpStatus expected) throws Exception {
        var result = mockMvc.perform(post(path)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expected.value()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
