package com.limitedgoods.limitedgoods.common.messaging.outbox.scheduler;

import com.limitedgoods.limitedgoods.common.messaging.outbox.publisher.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelayString = "${outbox.publish.delay-ms:500}")
    public void publish() {
        outboxPublisher.outboxPublishBatch();
    }
}