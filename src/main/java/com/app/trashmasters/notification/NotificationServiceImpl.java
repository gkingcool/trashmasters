// src/main/java/com/app/trashmasters/notification/NotificationServiceImpl.java
package com.app.trashmasters.notification;

import com.app.trashmasters.notification.model.Notification;
import com.app.trashmasters.notification.model.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Notification createNotification(String title, String message, NotificationType type) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(false);
        notification.setTimestamp(Instant.now());
        notification.setPriority(getPriorityForType(type));
        notification.setStatus("Under Review"); // Default status for new notifications

        return notificationRepository.save(notification);
    }

    @Override
    public Notification createBinNotification(String binId, String title, String message, NotificationType type) {
        Notification notification = createNotification(title, message, type);
        notification.setBinId(binId);
        notification.setActionUrl("/bins/" + binId);
        return notificationRepository.save(notification);
    }

    @Override
    public Notification createSensorNotification(String sensorId, String title, String message, NotificationType type) {
        Notification notification = createNotification(title, message, type);
        notification.setSensorId(sensorId);
        notification.setActionUrl("/sensors/" + sensorId);
        return notificationRepository.save(notification);
    }

    @Override
    public Notification createDriverNotification(String driverId, String title, String message, NotificationType type) {
        Notification notification = createNotification(title, message, type);
        notification.setDriverId(driverId);
        notification.setActionUrl("/driver");
        return notificationRepository.save(notification);
    }

    @Override
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByTimestampDesc();
    }

    @Override
    public List<Notification> getUnreadNotifications() {
        return notificationRepository.findByIsReadFalse();
    }

    @Override
    public List<Notification> getUnreadNotificationsByDriver(String driverId) {
        return notificationRepository.findByDriverIdAndIsReadFalse(driverId);
    }

    @Override
    public Notification markAsRead(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Override
    public void markAllAsRead() {
        List<Notification> unreadNotifications = notificationRepository.findByIsReadFalse();
        for (Notification notification : unreadNotifications) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    @Override
    public long getUnreadCount() {
        return notificationRepository.countByIsReadFalse();
    }

    @Override
    public long getUnreadCountByDriver(String driverId) {
        return notificationRepository.findByDriverIdAndIsReadFalse(driverId).size();
    }

    @Override
    public void deleteOldNotifications(int daysOld) {
        Instant cutoffDate = Instant.now().minus(daysOld, ChronoUnit.DAYS);
        List<Notification> oldNotifications = notificationRepository.findAllByOrderByTimestampDesc()
                .stream()
                .filter(n -> n.getTimestamp().isBefore(cutoffDate))
                .filter(Notification::isRead)
                .collect(Collectors.toList());

        notificationRepository.deleteAll(oldNotifications);
    }

    @Override
    public Notification updateStatus(String notificationId, String status) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        notification.setStatus(status);
        return notificationRepository.save(notification);
    }

    @Override
    public void deleteNotification(String notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    @Override
    public void deleteAllNotifications() {
        notificationRepository.deleteAll();
    }

    // Helper: Assign priority based on notification type
    private Integer getPriorityForType(NotificationType type) {
        return switch (type) {
            case ALERT -> 1;
            case WARNING -> 2;
            case SUCCESS -> 4;
            case INFO -> 5;
        };
    }

    @Override
    public Notification saveNotification(Notification notification) {
        if (notification.getTimestamp() == null) {
            notification.setTimestamp(Instant.now());
        }
        if (notification.getPriority() == null) {
            notification.setPriority(getPriorityForType(notification.getType()));
        }
        return notificationRepository.save(notification);
    }
}