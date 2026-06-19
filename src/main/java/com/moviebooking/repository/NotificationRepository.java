package com.moviebooking.repository;

import com.moviebooking.domain.entity.Notification;
import com.moviebooking.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByBookingIdAndType(Long bookingId, NotificationType type);
}
