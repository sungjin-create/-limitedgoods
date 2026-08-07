package com.limitedgoods.limitedgoods.messaging.outbox.admin.dto;

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
