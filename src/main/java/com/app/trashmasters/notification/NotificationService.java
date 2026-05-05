// src/main/java/com/app/trashmasters/notification/NotificationService.java
package com.app.trashmasters.notification;

import com.app.trashmasters.notification.model.Notification;
import com.app.trashmasters.notification.model.NotificationType;

import java.util.List;

public interface NotificationService {

    // Create a new generic notification
    Notification createNotification(String title, String message, NotificationType type);

    // Create notification linked to a bin
    Notification createBinNotification(String binId, String title, String message, NotificationType type);

    // Create notification linked to a sensor
    Notification createSensorNotification(String sensorId, String title, String message, NotificationType type);

    // Create notification linked to a driver
    Notification createDriverNotification(String driverId, String title, String message, NotificationType type);

    // Retrieve all notifications (newest first)
    List<Notification> getAllNotifications();

    // Retrieve only unread notifications
    List<Notification> getUnreadNotifications();

    // Retrieve unread notifications for a specific driver
    List<Notification> getUnreadNotificationsByDriver(String driverId);

    // Mark a single notification as read
    Notification markAsRead(String notificationId);

    // Mark all notifications as read
    void markAllAsRead();

    // Get count of unread notifications
    long getUnreadCount();

    // Get count of unread notifications for a specific driver
    long getUnreadCountByDriver(String driverId);

    // Delete old read notifications (cleanup task)
    void deleteOldNotifications(int daysOld);

    // Update notification status (for ticket workflow)
    Notification updateStatus(String notificationId, String status);

    // Delete a single notification by ID
    void deleteNotification(String notificationId);

    // Delete all notifications (admin use only)
    void deleteAllNotifications();

    Notification saveNotification(Notification notification);
}