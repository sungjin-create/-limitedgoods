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
    public int outboxPublishBatch() {
        List<OutboxEvent> messages = outboxRepository.lockPublishable(100);

        for (OutboxEvent message : messages) {
            try {
                kafkaTemplate.send(
                        message.topic(),
                        message.eventKey(),
                        message.payload()
                ).get(10, TimeUnit.SECONDS);

                outboxRepository.markPublished(message.id());
            } catch (Exception exception) {
                outboxRepository.markFailed(
                        message.id(),
                        exception.getMessage()
                );
            }
        }

        return messages.size();
    }
}