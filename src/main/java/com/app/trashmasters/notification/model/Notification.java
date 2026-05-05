// src/main/java/com/app/trashmasters/notification/model/Notification.java
package com.app.trashmasters.notification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    @Indexed
    private String title;

    private String message;

    private NotificationType type; // ALERT, WARNING, SUCCESS, INFO

    private boolean isRead;

    private Instant timestamp;

    private String binId;        // Related bin (if applicable)

    private String sensorId;     // Related sensor (if applicable)

    private String driverId;     // Related driver (if applicable)

    private Integer priority;    // 1 = Highest, 5 = Lowest

    private String actionUrl;    // Optional: Link to related page

    // ✅ Status field for ticket tracking
    private String status; // "Under Review", "In Progress", "Resolved"
}