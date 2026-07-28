package com.limitedgoods.limitedgoods.event.outbox.entity;

import com.limitedgoods.limitedgoods.event.outbox.exception.OutboxEventClaimOwnershipLostException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OutboxEventType eventType;

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    private String aggregateType;
    private Long aggregateId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private int retryCount;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_tried_at")
    private LocalDateTime lastTriedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "claim_token", columnDefinition = "uuid")
    private UUID claimToken;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "lease_expired_count", nullable = false)
    private int leaseExpiredCount;

    public static OutboxEvent create(
            OutboxEventType eventType,
            String aggregateType,
            Long aggregateId,
            String payload
    ) {
        LocalDateTime now = LocalDateTime.now();

        OutboxEvent event = new OutboxEvent();

        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.payload = payload;

        event.status = OutboxEventStatus.PENDING;
        event.retryCount = 0;
        event.attemptCount = 0;
        event.leaseExpiredCount = 0;
        event.nextAttemptAt = now;
        event.createdAt = now;

        return event;
    }

    public UUID markProcessing(LocalDateTime now) {
        if (status == OutboxEventStatus.PUBLISHED
                || status == OutboxEventStatus.DEAD) {
            throw new IllegalStateException(
                    "Completed outbox event cannot be claimed"
            );
        }

        if (status == OutboxEventStatus.PROCESSING) {
            leaseExpiredCount++;
        }

        UUID newClaimToken = UUID.randomUUID();

        status = OutboxEventStatus.PROCESSING;
        processingStartedAt = now;
        claimToken = newClaimToken;
        attemptCount++;
        lastTriedAt = now;

        return newClaimToken;
    }

    public boolean isOwnedBy(UUID expectedClaimToken) {
        return status == OutboxEventStatus.PROCESSING
                && Objects.equals(
                claimToken,
                expectedClaimToken
        );
    }

    public void markClaimPublished(
            UUID expectedClaimToken,
            LocalDateTime now
    ) {
        requireOwnership(expectedClaimToken);

        status = OutboxEventStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;

        processingStartedAt = null;
        claimToken = null;
    }

    public void markClaimFailed(
            UUID expectedClaimToken,
            String errorMessage,
            int maxAttempts,
            LocalDateTime now
    ) {
        requireOwnership(expectedClaimToken);

        retryCount++;
        lastError = truncateError(errorMessage);
        lastTriedAt = now;

        processingStartedAt = null;
        claimToken = null;

        if (attemptCount >= maxAttempts) {
            status = OutboxEventStatus.DEAD;
            return;
        }

        status = OutboxEventStatus.FAILED;

        long backoffMinutes = Math.min(
                30,
                1L << Math.min(retryCount - 1, 5)
        );

        nextAttemptAt = now.plusMinutes(backoffMinutes);
    }

    private void requireOwnership(UUID expectedClaimToken) {
        if (!isOwnedBy(expectedClaimToken)) {
            throw new OutboxEventClaimOwnershipLostException(
                    id,
                    expectedClaimToken
            );
        }
    }

    private String truncateError(String errorMessage) {
        String message =
                errorMessage == null || errorMessage.isBlank()
                        ? "Unknown outbox processing error"
                        : errorMessage;

        return message.length() <= 2000
                ? message
                : message.substring(0, 2000);
    }

}
