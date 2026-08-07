package com.limitedgoods.limitedgoods.messaging.outbox.infrastructure.scheduler;

import com.limitedgoods.limitedgoods.messaging.outbox.application.OutboxEventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxDispatchScheduler {

    private final OutboxEventDispatcher outboxEventDispatcher;

    @Scheduled(fixedDelayString = "${outbox.publish.delay-ms:500}")
    public void dispatch() {
        outboxEventDispatcher.dispatchBatch();
    }
}
