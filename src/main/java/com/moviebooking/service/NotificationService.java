package com.moviebooking.service;

import com.moviebooking.domain.entity.Booking;
import com.moviebooking.domain.entity.Notification;
import com.moviebooking.domain.entity.User;
import com.moviebooking.domain.enums.NotificationStatus;
import com.moviebooking.domain.enums.NotificationType;
import com.moviebooking.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Async("notificationExecutor")
    @Transactional
    public void sendBookingConfirmation(User user, Booking booking) {
        send(user, booking, NotificationType.BOOKING_CONFIRMATION, Map.of(
                "bookingId", booking.getId(),
                "showId", booking.getShow().getId(),
                "totalAmount", booking.getTotalAmount()
        ));
    }

    @Async("notificationExecutor")
    @Transactional
    public void sendBookingCancelled(User user, Booking booking, Map<String, Object> extra) {
        var payload = new HashMap<String, Object>();
        payload.put("bookingId", booking.getId());
        payload.put("showId", booking.getShow().getId());
        payload.putAll(extra);
        send(user, booking, NotificationType.BOOKING_CANCELLED, payload);
    }

    @Async("notificationExecutor")
    @Transactional
    public void sendShowReminder(User user, Booking booking) {
        send(user, booking, NotificationType.SHOW_REMINDER, Map.of(
                "bookingId", booking.getId(),
                "showId", booking.getShow().getId(),
                "startTime", booking.getShow().getStartTime().toString()
        ));
    }

    @Async("notificationExecutor")
    @Transactional
    public void sendShowCancelled(User user, Booking booking) {
        send(user, booking, NotificationType.SHOW_CANCELLED, Map.of(
                "bookingId", booking.getId(),
                "showId", booking.getShow().getId()
        ));
    }

    private void send(User user, Booking booking, NotificationType type, Map<String, Object> payload) {
        var notification = Notification.builder()
                .user(user)
                .booking(booking)
                .type(type)
                .status(NotificationStatus.PENDING)
                .payload(payload)
                .build();
        notification = notificationRepository.save(notification);
        try {
            log.info("Notification [{}] to user {}: {}", type, user.getEmail(), payload);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            log.error("Failed to send notification {}", notification.getId(), e);
        }
        notificationRepository.save(notification);
    }
}
