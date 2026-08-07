package com.limitedgoods.limitedgoods.messaging.outbox.application;

public interface OutboxEventDispatcher {

    void dispatchBatch();
}
