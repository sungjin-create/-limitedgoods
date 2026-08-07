package com.limitedgoods.limitedgoods.notification.dto;

import com.limitedgoods.limitedgoods.notification.entity.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(
            Notification notification
    ) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}