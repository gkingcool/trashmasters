// src/main/java/com/app/trashmasters/notification/NotificationRepository.java
package com.app.trashmasters.notification;

import com.app.trashmasters.notification.model.Notification;
import com.app.trashmasters.notification.model.NotificationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    // Get all unread notifications
    List<Notification> findByIsReadFalse();

    // Get all notifications sorted by timestamp (newest first)
    List<Notification> findAllByOrderByTimestampDesc();

    // Get unread notifications for a specific bin
    List<Notification> findByBinIdAndIsReadFalse(String binId);

    // Get unread notifications for a specific driver
    List<Notification> findByDriverIdAndIsReadFalse(String driverId);

    // Count unread notifications
    long countByIsReadFalse();

    // Get notifications by type (unread only)
    List<Notification> findByTypeAndIsReadFalse(NotificationType type);

    // Get notifications for a specific driver (all)
    List<Notification> findByDriverIdOrderByTimestampDesc(String driverId);
}