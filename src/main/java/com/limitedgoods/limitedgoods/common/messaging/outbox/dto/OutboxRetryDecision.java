package com.limitedgoods.limitedgoods.common.messaging.outbox.dto;

import java.time.Instant;

public record OutboxRetryDecision(
        OutboxStatus status,
        int failureCount,
        Instant nextAttemptAt
) {
    public static OutboxRetryDecision retry(int failureCount, Instant nextAttemptAt) {
        return new OutboxRetryDecision(
                OutboxStatus.FAILED,
                failureCount,
                nextAttemptAt
        );
    }

    public static OutboxRetryDecision dead(int failureCount) {
        return new OutboxRetryDecision(
                OutboxStatus.DEAD,
                failureCount,
                null
        );
    }

    public boolean isDead() {
        return status == OutboxStatus.DEAD;
    }
}