package com.limitedgoods.limitedgoods.common.messaging.outbox.scheduler;

import com.limitedgoods.limitedgoods.common.messaging.outbox.service.OutboxRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:500}")
    public void relay() {
        relay.publishBatch();
    }
}