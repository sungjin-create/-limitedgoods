package com.limitedgoods.limitedgoods.common.messaging.outbox.publisher;

import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxEvent;
import com.limitedgoods.limitedgoods.common.messaging.outbox.repository.OutboxJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void outboxPublishBatch() {
        List<OutboxEvent> outboxEvents = outboxRepository.lockPublishable(100);

        for (OutboxEvent event : outboxEvents) {
            try {
                kafkaTemplate.send(
                        event.topic(),
                        event.eventKey(),
                        event.payload()
                ).get(10, TimeUnit.SECONDS);

                outboxRepository.markPublished(event.id());
            } catch (Exception exception) {
                outboxRepository.markFailed(
                        event.id(),
                        exception.getMessage()
                );
            }
        }
    }
}