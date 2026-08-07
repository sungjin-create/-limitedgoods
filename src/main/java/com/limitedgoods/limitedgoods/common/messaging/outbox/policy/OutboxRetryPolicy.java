package com.limitedgoods.limitedgoods.common.messaging.outbox.policy;

import com.limitedgoods.limitedgoods.common.messaging.outbox.config.OutboxPublishProperties;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxRetryDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutboxRetryPolicy {

    private final OutboxPublishProperties outboxPublishProperties;

    public OutboxRetryDecision decide(int currentAttempts) {
        int failureCount = currentAttempts + 1;

        if (failureCount >= outboxPublishProperties.maxAttempts()) {
            return OutboxRetryDecision.dead(failureCount);
        }

        Duration delay = calculateBackoff(currentAttempts);

        return OutboxRetryDecision.retry(failureCount, Instant.now().plus(delay));
    }

    private Duration calculateBackoff(int currentAttempts) {
        long initialSeconds = outboxPublishProperties.initialBackoff().toSeconds();
        long maximumSeconds = outboxPublishProperties.maxBackoff().toSeconds();
        long delaySeconds = initialSeconds;

        for (int attempt = 0; attempt < currentAttempts && delaySeconds < maximumSeconds; attempt++) {
            delaySeconds = Math.min(maximumSeconds, delaySeconds * 2);
        }

        return Duration.ofSeconds(delaySeconds);
    }
}