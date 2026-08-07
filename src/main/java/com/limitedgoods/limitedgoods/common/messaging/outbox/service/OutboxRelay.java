package com.limitedgoods.limitedgoods.common.messaging.outbox.service;

import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxMessage;
import com.limitedgoods.limitedgoods.common.messaging.outbox.repository.OutboxJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxJdbcRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public int publishBatch() {
        List<OutboxMessage> messages = repository.lockPublishable(100);

        for (OutboxMessage message : messages) {
            try {
                kafkaTemplate.send(
                        message.topic(),
                        message.eventKey(),
                        message.payload()
                ).get(10, TimeUnit.SECONDS);

                repository.markPublished(message.id());
            } catch (Exception exception) {
                repository.markFailed(
                        message.id(),
                        exception.getMessage()
                );
            }
        }

        return messages.size();
    }
}