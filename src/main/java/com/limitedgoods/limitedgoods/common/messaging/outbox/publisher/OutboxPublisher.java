package com.limitedgoods.limitedgoods.common.messaging.outbox.publisher;

import com.limitedgoods.limitedgoods.common.messaging.outbox.config.OutboxPublishProperties;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxEvent;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxRetryDecision;
import com.limitedgoods.limitedgoods.common.messaging.outbox.policy.OutboxRetryPolicy;
import com.limitedgoods.limitedgoods.common.messaging.outbox.repository.OutboxJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRetryPolicy outboxRetryPolicy;
    private final OutboxPublishProperties outboxPublishProperties;

    @Transactional
    public void outboxPublishBatch() {
        List<OutboxEvent> events = outboxRepository.lockPublishable(outboxPublishProperties.batchSize());

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                    event.topic(),
                    event.eventKey(),
                    event.payload()
            ).get(
                    outboxPublishProperties.sendTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            outboxRepository.markPublished(event.id());

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            handleFailure(event, exception);
        } catch (ExecutionException | TimeoutException exception) {
            handleFailure(event, exception);
        }
    }

    private void handleFailure(OutboxEvent event, Exception exception) {
        OutboxRetryDecision decision = outboxRetryPolicy.decide(event.attempts());

        if (decision.isDead()) {
            outboxRepository.markDead(
                    event.id(),
                    decision.failureCount(),
                    exception.getMessage()
            );

            return;
        }

        outboxRepository.markFailed(
                event.id(),
                decision.failureCount(),
                decision.nextAttemptAt(),
                exception.getMessage()
        );
    }
}