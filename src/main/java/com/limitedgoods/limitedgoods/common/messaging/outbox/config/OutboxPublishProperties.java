package com.limitedgoods.limitedgoods.common.messaging.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox.publish")
public record OutboxPublishProperties(
        long delayMs,
        int batchSize,
        Duration sendTimeout,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff
) {
    public OutboxPublishProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("outbox.publish.batch-size는 1 이상이어야 합니다.");
        }

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("outbox.publish.max-attempts는 1 이상이어야 합니다.");
        }
    }
}