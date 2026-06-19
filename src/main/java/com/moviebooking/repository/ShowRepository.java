package com.moviebooking.repository;

import com.moviebooking.domain.entity.Show;
import com.moviebooking.domain.enums.ShowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    @Query("""
        SELECT s FROM Show s
        JOIN FETCH s.movie
        JOIN FETCH s.screen sc
        JOIN FETCH sc.theater t
        JOIN FETCH t.city
        LEFT JOIN FETCH s.refundPolicy
        WHERE s.status = :status
        AND (:skipCity = true OR t.city.id = :cityId)
        AND (:skipMovie = true OR s.movie.id = :movieId)
        AND (:skipDateStart = true OR s.startTime >= :dateStart)
        AND (:skipDateEnd = true OR s.startTime < :dateEnd)
        ORDER BY s.startTime
        """)
    List<Show> findShows(
            @Param("status") ShowStatus status,
            @Param("skipCity") boolean skipCity,
            @Param("cityId") Long cityId,
            @Param("skipMovie") boolean skipMovie,
            @Param("movieId") Long movieId,
            @Param("skipDateStart") boolean skipDateStart,
            @Param("dateStart") Instant dateStart,
            @Param("skipDateEnd") boolean skipDateEnd,
            @Param("dateEnd") Instant dateEnd);

    @Query("""
        SELECT s FROM Show s
        WHERE s.status = :status
        AND s.startTime >= :windowStart
        AND s.startTime <= :windowEnd
        """)
    List<Show> findShowsStartingInWindow(
            @Param("status") ShowStatus status,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
