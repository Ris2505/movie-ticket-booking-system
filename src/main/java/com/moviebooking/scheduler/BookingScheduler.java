package com.moviebooking.scheduler;

import com.moviebooking.domain.enums.BookingStatus;
import com.moviebooking.domain.enums.NotificationType;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.NotificationRepository;
import com.moviebooking.service.HoldService;
import com.moviebooking.service.NotificationService;
import com.moviebooking.config.AppProperties;
import com.moviebooking.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingScheduler {

    private final HoldService holdService;
    private final ShowRepository showRepository;
    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.hold-expiry-cron:*/30 * * * * *}")
    public void expireHolds() {
        int released = holdService.expireHeldSeats();
        if (released > 0) {
            log.info("Released {} expired held seats", released);
        }
    }

    @Scheduled(cron = "${app.reminder-cron:0 0 * * * *}")
    public void sendReminders() {
        int hours = appProperties.getReminderHoursBeforeShow();
        Instant now = Instant.now();
        Instant windowStart = now.plus(hours, ChronoUnit.HOURS).minus(30, ChronoUnit.MINUTES);
        Instant windowEnd = now.plus(hours, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);

        var shows = showRepository.findShowsStartingInWindow(
                com.moviebooking.domain.enums.ShowStatus.SCHEDULED, windowStart, windowEnd);
        for (var show : shows) {
            var bookings = bookingRepository.findByShowIdWithUser(show.getId());
            for (var booking : bookings) {
                if (booking.getStatus() == BookingStatus.CANCELLED) {
                    continue;
                }
                if (notificationRepository.existsByBookingIdAndType(booking.getId(), NotificationType.SHOW_REMINDER)) {
                    continue;
                }
                notificationService.sendShowReminder(booking.getUser(), booking);
            }
        }
    }
}
