package com.limitedgoods.limitedgoods.event.outbox.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limitedgoods.limitedgoods.analytics.handler.InternalAnalyticsEventHandler;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEvent;
import com.limitedgoods.limitedgoods.event.outbox.exception.InternalOutboxProcessingException;
import com.limitedgoods.limitedgoods.event.outbox.repository.OutboxEventRepository;
import com.limitedgoods.limitedgoods.event.outbox.service.ClaimedOutboxEvent;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventStateService;
import com.limitedgoods.limitedgoods.event.payload.order.*;
import com.limitedgoods.limitedgoods.notification.handler.InternalEmailEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalOutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventStateService outboxEventStateService;
    private final InternalEmailEventHandler emailEventHandler;
    private final InternalAnalyticsEventHandler analyticsEventHandler;
    private final ObjectMapper objectMapper;

    public void process(ClaimedOutboxEvent claim) {
        OutboxEvent outboxEvent =
                outboxEventRepository
                        .findById(claim.eventId())
                        .orElseThrow();

        /*
         * lease 만료 후 다른 서버가 다시 claim한 경우
         * 오래된 서버는 처리하지 않습니다.
         */
        if (!outboxEvent.isOwnedBy(claim.claimToken())) {
            return;
        }

        switch (outboxEvent.getEventType()) {
            case ORDER_CREATED -> processOrderCreated(outboxEvent);

            case ORDER_PAID -> processOrderPaid(outboxEvent);

            case PAYMENT_FAILED -> processPaymentFailed(outboxEvent);

            case ORDER_EXPIRED -> processOrderExpired(outboxEvent);

            case ORDER_CANCELED -> processOrderCanceled(outboxEvent);
        }

        outboxEventStateService.markPublished(claim, LocalDateTime.now());
    }

    private void processOrderPaid(OutboxEvent outboxEvent) {
        OrderPaidEvent event = readEvent(
                outboxEvent,
                OrderPaidEvent.class
        );

        InternalOutboxProcessingException failure =
                new InternalOutboxProcessingException(outboxEvent.getId());

        boolean failed = false;

        try {
            emailEventHandler.handle(outboxEvent.getId(), event);
        } catch (RuntimeException exception) {
            failed = true;
            failure.addSuppressed(exception);

            log.error(
                    "event=internal_email_event_failed eventId={}",
                    outboxEvent.getId(),
                    exception
            );
        }

        try {
            analyticsEventHandler.handle(outboxEvent.getId(), event);
        } catch (RuntimeException exception) {
            failed = true;
            failure.addSuppressed(exception);

            log.error(
                    "event=internal_analytics_event_failed eventId={}",
                    outboxEvent.getId(),
                    exception
            );
        }

        if (failed) {
            throw failure;
        }
    }

    private void processOrderCreated(OutboxEvent outboxEvent) {
        OrderCreatedEvent event = readEvent(
                outboxEvent,
                OrderCreatedEvent.class
        );

        analyticsEventHandler.handle(outboxEvent.getId(), event);
    }

    private void processPaymentFailed(OutboxEvent outboxEvent) {
        PaymentFailedEvent event = readEvent(
                outboxEvent,
                PaymentFailedEvent.class
        );

        analyticsEventHandler.handle(outboxEvent.getId(), event);
    }

    private void processOrderExpired(OutboxEvent outboxEvent) {
        OrderExpiredEvent event = readEvent(
                outboxEvent,
                OrderExpiredEvent.class
        );

        analyticsEventHandler.handle(outboxEvent.getId(), event);
    }

    private void processOrderCanceled(OutboxEvent outboxEvent) {
        OrderCanceledEvent event = readEvent(
                outboxEvent,
                OrderCanceledEvent.class
        );

        analyticsEventHandler.handle(outboxEvent.getId(), event);
    }

    private <T> T readEvent(
            OutboxEvent outboxEvent,
            Class<T> eventClass
    ) {
        try {
            return objectMapper.readValue(
                    outboxEvent.getPayload(),
                    eventClass
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    outboxEvent.getEventType()
                            + " event payload is invalid",
                    exception
            );
        }
    }
}