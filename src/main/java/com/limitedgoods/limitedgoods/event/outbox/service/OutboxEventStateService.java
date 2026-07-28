package com.limitedgoods.limitedgoods.event.outbox.service;

import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEvent;
import com.limitedgoods.limitedgoods.event.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventStateService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public List<ClaimedOutboxEvent> claimBatch(
            LocalDateTime now,
            LocalDateTime staleBefore,
            int maxAttempts,
            int batchSize
    ) {
        outboxEventRepository.markExhaustedProcessingAsDead(staleBefore, maxAttempts);

        List<OutboxEvent> events =
                outboxEventRepository.lockClaimableEvents(
                        now,
                        staleBefore,
                        maxAttempts,
                        batchSize
                );

        List<ClaimedOutboxEvent> result = new ArrayList<>();

        for(OutboxEvent event : events) {
            ClaimedOutboxEvent claimedEvent =
                    new ClaimedOutboxEvent(
                            event.getId(),
                            event.markProcessing(now),
                            event.getEventType()
                    );

            result.add(claimedEvent);
        }

        return result;
    }

    @Transactional
    public void markPublished(ClaimedOutboxEvent claim, LocalDateTime now) {
        OutboxEvent event = getForUpdate(claim.eventId());

        event.markClaimPublished(claim.claimToken(), now);
    }

    @Transactional
    public void markFailed(
            ClaimedOutboxEvent claim,
            Throwable exception,
            int maxAttempts,
            LocalDateTime now
    ) {
        OutboxEvent event = getForUpdate(claim.eventId());

        event.markClaimFailed(
                claim.claimToken(),
                exception.getMessage(),
                maxAttempts,
                now
        );
    }

    private OutboxEvent getForUpdate(Long eventId) {
        return outboxEventRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Outbox event not found: " + eventId));
    }
}