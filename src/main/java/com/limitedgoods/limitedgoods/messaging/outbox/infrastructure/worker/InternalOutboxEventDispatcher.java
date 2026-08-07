package com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.worker;

import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxEventDispatcher;
import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxRetryPolicy;
import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.config.OutboxProperties;
import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.jdbc.JdbcOutboxRepository;
import com.limitedgoods.limitedgoods.messaging.outbox.model.OutboxRecord;
import com.limitedgoods.limitedgoods.messaging.outbox.model.OutboxRetryDecision;
import com.limitedgoods.limitedgoods.notification.application.event.OrderPaidNotificationEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Profile("worker")
@Service
@RequiredArgsConstructor
public class InternalOutboxEventDispatcher implements OutboxEventDispatcher {

    private final JdbcOutboxRepository outboxRepository;
    private final OrderPaidNotificationEventHandler notificationHandler;
    private final OutboxRetryPolicy outboxRetryPolicy;
    private final OutboxProperties outboxProperties;

    @Override
    @Transactional
    public void dispatchBatch() {
        List<OutboxRecord> events = outboxRepository.lockPublishable(outboxProperties.batchSize());

        for (OutboxRecord event : events) {
            process(event);
        }
    }

    private void process(OutboxRecord event) {
        try {
            notificationHandler.handle(event.payload());
            outboxRepository.markPublished(event.id());
        } catch (Exception exception) {
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
