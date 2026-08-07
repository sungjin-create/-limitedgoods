package com.limitedgoods.limitedgoods.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.limitedgoods.limitedgoods.notification.application.event.OrderPaidNotificationEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Profile("kafka")
@Component
@RequiredArgsConstructor
public class KafkaOrderPaidNotificationListener {

    private final OrderPaidNotificationEventHandler notificationHandler;

    @KafkaListener(
            topics = "${app.kafka.topics.order-lifecycle.name}",
            groupId = "${app.kafka.consumer.notification.group-id}"
    )
    public void consume(String message) throws JsonProcessingException {
        notificationHandler.handle(message);
    }
}
