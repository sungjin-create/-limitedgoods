package com.limitedgoods.limitedgoods.event.outbox.entity;

public enum OutboxEventType {
    ORDER_CREATED,
    ORDER_PAID,
    PAYMENT_FAILED,
    ORDER_EXPIRED,
    ORDER_CANCELED
}
