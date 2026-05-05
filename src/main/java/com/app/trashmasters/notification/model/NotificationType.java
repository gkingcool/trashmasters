// src/main/java/com/app/trashmasters/notification/model/NotificationType.java
package com.app.trashmasters.notification.model;

public enum NotificationType {
    ALERT,      // Critical issues (bin overflow, sensor offline)
    WARNING,    // Important but not urgent (low battery, maintenance due)
    SUCCESS,    // Positive events (route completed, bin emptied)
    INFO        // General information (system updates, new features)
}