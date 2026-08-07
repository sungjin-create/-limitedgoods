package com.limitedgoods.limitedgoods.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitedgoods.limitedgoods.common.messaging.inbox.ProcessedEventRepository;
import com.limitedgoods.limitedgoods.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderPaidConsumer {

    private static final String CONSUMER_NAME =
            "notification-order-paid-v1";

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;

    @Transactional
    @KafkaListener(
            topics = "orders.lifecycle.v1",
            groupId = "notification-service"
    )
    public void consume(String message) throws JsonProcessingException {
        JsonNode envelope = objectMapper.readTree(message);

        if (!"order.paid.v1".equals(
                envelope.get("eventType").asText()
        )) {
            return;
        }

        UUID eventId = UUID.fromString(
                envelope.get("eventId").asText()
        );

        if (!processedEventRepository.tryInsert(
                CONSUMER_NAME,
                eventId
        )) {
            return;
        }

        JsonNode data = envelope.get("data");

//        notificationService.sendPaymentCompleted(
//                data.get("userId").asLong(),
//                data.get("orderId").asLong()
//        );
    }
}