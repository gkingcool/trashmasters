// src/main/java/com/app/trashmasters/notification/NotificationController.java
package com.app.trashmasters.notification;

import com.app.trashmasters.notification.model.Notification;
import com.app.trashmasters.notification.model.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Create a new notification (POST)
    @PostMapping
    public ResponseEntity<Notification> createNotification(@RequestBody Notification notification) {
        // Set defaults for missing fields
        if (notification.getTimestamp() == null) {
            notification.setTimestamp(Instant.now());
        }
        if (!notification.isRead()) {
            notification.setRead(false);
        }
        if (notification.getPriority() == null) {
            notification.setPriority(3); // Default priority
        }
        if (notification.getType() == null) {
            notification.setType(NotificationType.INFO);
        }

        // Use service to save the complete notification (preserves driverId)
        return ResponseEntity.ok(notificationService.saveNotification(notification));
    }

    // Get all notifications
    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return ResponseEntity.ok(notificationService.getAllNotifications());
    }

    // Get unread notifications only
    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications() {
        return ResponseEntity.ok(notificationService.getUnreadNotifications());
    }

    // Get unread count
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        Map<String, Long> response = new HashMap<>();
        response.put("count", notificationService.getUnreadCount());
        return ResponseEntity.ok(response);
    }

    // Get unread notifications for a specific driver
    @GetMapping("/driver/{driverId}/unread")
    public ResponseEntity<List<Notification>> getUnreadByDriver(@PathVariable String driverId) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsByDriver(driverId));
    }

    // Get unread count for a specific driver
    @GetMapping("/driver/{driverId}/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCountByDriver(@PathVariable String driverId) {
        Map<String, Long> response = new HashMap<>();
        response.put("count", notificationService.getUnreadCountByDriver(driverId));
        return ResponseEntity.ok(response);
    }

    // Mark single notification as read
    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    // Mark all notifications as read
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }

    // Update notification status (for ticket workflow)
    @PutMapping("/{id}/status")
    public ResponseEntity<Notification> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {

        String status = request.get("status");
        if (status == null || status.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(notificationService.updateStatus(id, status));
    }

    // Delete single notification
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.ok().build();
    }

    // Delete all notifications (admin only)
    @DeleteMapping("/all")
    public ResponseEntity<Void> deleteAllNotifications() {
        notificationService.deleteAllNotifications();
        return ResponseEntity.ok().build();
    }

    // Cleanup: Delete old read notifications
    @DeleteMapping("/cleanup/{daysOld}")
    public ResponseEntity<Void> deleteOldNotifications(@PathVariable int daysOld) {
        notificationService.deleteOldNotifications(daysOld);
        return ResponseEntity.ok().build();
    }
}