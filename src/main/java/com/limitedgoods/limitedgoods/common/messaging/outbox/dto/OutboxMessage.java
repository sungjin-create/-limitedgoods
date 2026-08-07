package com.limitedgoods.limitedgoods.common.messaging.outbox.dto;

import java.util.UUID;

public record OutboxMessage(
        UUID id,
        String topic,
        String eventKey,
        String payload,
        int attempts
) {
}