package com.limitedgoods.limitedgoods.messaging.outbox.application;

import com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.config.OutboxProperties;
import com.limitedgoods.limitedgoods.messaging.outbox.model.OutboxRetryDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutboxRetryPolicy {

    private final OutboxProperties outboxProperties;

    public OutboxRetryDecision decide(int currentAttempts) {
        int failureCount = currentAttempts + 1;

        if (failureCount >= outboxProperties.maxAttempts()) {
            return OutboxRetryDecision.dead(failureCount);
        }

        Duration delay = calculateBackoff(currentAttempts);

        return OutboxRetryDecision.retry(failureCount, Instant.now().plus(delay));
    }

    private Duration calculateBackoff(int currentAttempts) {
        long initialSeconds = outboxProperties.initialBackoff().toSeconds();
        long maximumSeconds = outboxProperties.maxBackoff().toSeconds();
        long delaySeconds = initialSeconds;

        for (int attempt = 0; attempt < currentAttempts && delaySeconds < maximumSeconds; attempt++) {
            delaySeconds = Math.min(maximumSeconds, delaySeconds * 2);
        }

        return Duration.ofSeconds(delaySeconds);
    }
}
