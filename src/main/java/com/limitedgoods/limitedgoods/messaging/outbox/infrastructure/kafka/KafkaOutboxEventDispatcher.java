package com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.kafka;

import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxEventDispatcher;
import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxRetryPolicy;
import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.config.OutboxProperties;
import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.jdbc.JdbcOutboxRepository;
import com.limitedgoods.limitedgoods.messaging.outbox.model.OutboxRecord;
import com.limitedgoods.limitedgoods.messaging.outbox.model.OutboxRetryDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Profile("kafka")
@Service
@RequiredArgsConstructor
public class KafkaOutboxEventDispatcher implements OutboxEventDispatcher {

    private final JdbcOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxRetryPolicy outboxRetryPolicy;
    private final OutboxProperties outboxProperties;

    @Override
    @Transactional
    public void dispatchBatch() {
        List<OutboxRecord> events = outboxRepository.lockPublishable(outboxProperties.batchSize());

        for (OutboxRecord event : events) {
            publish(event);
        }
    }

    private void publish(OutboxRecord event) {
        try {
            kafkaTemplate.send(
                    event.topic(),
                    event.eventKey(),
                    event.payload()
            ).get(
                    outboxProperties.sendTimeout().toMillis(),
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

    private void handleFailure(OutboxRecord event, Exception exception) {
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
