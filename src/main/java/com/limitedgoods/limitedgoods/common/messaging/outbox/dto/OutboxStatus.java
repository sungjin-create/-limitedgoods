package com.limitedgoods.limitedgoods.common.messaging.outbox.dto;

public enum OutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD
}