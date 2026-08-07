package com.limitedgoods.limitedgoods.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitedgoods.limitedgoods.common.messaging.inbox.ProcessedEventRepository;
import com.limitedgoods.limitedgoods.notification.entity.Notification;
import com.limitedgoods.limitedgoods.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderPaidNotificationConsumer {

    private static final String CONSUMER_NAME = "notification.order-paid.v1";

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    @KafkaListener(
            topics = "orders.lifecycle.v1",
            groupId = "limitedgoods-notification"
    )
    public void consume(String message) throws JsonProcessingException {

        JsonNode envelope = objectMapper.readTree(message);

        if (!"order.paid.v1".equals(
                envelope.path("eventType").asText()
        )) {
            return;
        }

        UUID eventId = UUID.fromString(
                envelope.path("eventId").asText()
        );

        boolean firstProcessing =
                processedEventRepository.tryInsert(
                        CONSUMER_NAME,
                        eventId
                );

        if (!firstProcessing) {
            return;
        }

        JsonNode data = envelope.path("data");

        Long userId = data.path("userId").asLong();
        Long orderId = data.path("orderId").asLong();

        Notification notification = Notification.orderPaid(
                eventId,
                userId,
                orderId
        );

        notificationRepository.save(notification);
    }
}