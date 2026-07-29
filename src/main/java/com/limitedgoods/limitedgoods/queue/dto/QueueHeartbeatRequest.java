package com.limitedgoods.limitedgoods.queue.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QueueHeartbeatRequest(
        @NotNull
        @Positive
        Long productId
) {
}