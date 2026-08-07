package com.limitedgoods.limitedgoods.messaging.outbox.model;

public enum OutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD
}
