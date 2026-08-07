package com.limitedgoods.limitedgoods.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static Notification orderPaid(
            UUID eventId,
            Long userId,
            Long orderId
    ) {
        Notification notification = new Notification();

        notification.sourceEventId = eventId;
        notification.userId = userId;
        notification.type = NotificationType.ORDER_PAID;
        notification.title = "결제가 완료되었습니다";
        notification.message =
                "주문번호 " + orderId + "의 결제가 완료되었습니다.";
        notification.link = "/orders/" + orderId;
        notification.read = false;
        notification.createdAt = LocalDateTime.now();

        return notification;
    }

    public void markAsRead() {
        if (read) {
            return;
        }

        read = true;
        readAt = LocalDateTime.now();
    }
}