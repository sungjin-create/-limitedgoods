package com.limitedgoods.limitedgoods.messaging.outbox.model;

import java.util.UUID;

public record OutboxRecord(
        UUID id,
        String topic,
        String eventKey,
        String payload,
        int attempts
) {
}
