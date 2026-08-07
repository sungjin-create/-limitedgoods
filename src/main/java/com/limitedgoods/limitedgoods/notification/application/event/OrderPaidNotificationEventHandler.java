package com.limitedgoods.limitedgoods.notification.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitedgoods.limitedgoods.messaging.inbox.infrastructure.jdbc.JdbcProcessedEventRepository;
import com.limitedgoods.limitedgoods.notification.entity.Notification;
import com.limitedgoods.limitedgoods.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPaidNotificationEventHandler {

    private static final String CONSUMER_NAME = "notification.order-paid.v1";

    private final ObjectMapper objectMapper;
    private final JdbcProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(String message) throws JsonProcessingException {
        JsonNode envelope = objectMapper.readTree(message);

        if (!"order.paid.v1".equals(envelope.path("eventType").asText())) {
            return;
        }

        UUID eventId = UUID.fromString(envelope.path("eventId").asText());
        boolean firstProcessing = processedEventRepository.tryInsert(CONSUMER_NAME, eventId);

        if (!firstProcessing) {
            return;
        }

        JsonNode data = envelope.path("data");
        Long userId = data.path("userId").asLong();
        Long orderId = data.path("orderId").asLong();

        notificationRepository.save(Notification.orderPaid(eventId, userId, orderId));
    }
}
