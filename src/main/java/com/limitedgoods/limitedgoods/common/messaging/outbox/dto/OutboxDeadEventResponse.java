package com.limitedgoods.limitedgoods.common.messaging.outbox.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxDeadEventResponse(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int eventVersion,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime deadAt
) {
}
