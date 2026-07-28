package com.limitedgoods.limitedgoods.event.outbox.worker;

import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.exception.OutboxEventClaimOwnershipLostException;
import com.limitedgoods.limitedgoods.event.outbox.processor.InternalOutboxProcessor;
import com.limitedgoods.limitedgoods.event.outbox.service.ClaimedOutboxEvent;
import com.limitedgoods.limitedgoods.event.outbox.service.OutboxEventStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalOutboxWorkerTest {

    @Mock OutboxEventStateService stateService;
    @Mock InternalOutboxProcessor processor;

    private InternalOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new InternalOutboxWorker(stateService, processor);
        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        ReflectionTestUtils.setField(worker, "batchSize", 100);
        ReflectionTestUtils.setField(worker, "processingLeaseSeconds", 300L);
    }

    @Test
    void claimsConfiguredBatchAndProcessesEveryEvent() {
        ClaimedOutboxEvent first = claim(1L, OutboxEventType.ORDER_CREATED);
        ClaimedOutboxEvent second = claim(2L, OutboxEventType.ORDER_PAID);
        when(stateService.claimBatch(any(), any(), eq(5), eq(100)))
                .thenReturn(List.of(first, second));

        worker.processPendingEvents();

        verify(processor).process(first);
        verify(processor).process(second);
        verify(stateService, never()).markFailed(any(), any(), anyInt(), any());
    }

    @Test
    void processingFailureIsRecordedAndNextClaimStillRuns() {
        ClaimedOutboxEvent first = claim(1L, OutboxEventType.ORDER_PAID);
        ClaimedOutboxEvent second = claim(2L, OutboxEventType.ORDER_EXPIRED);
        RuntimeException failure = new RuntimeException("projection failed");
        when(stateService.claimBatch(any(), any(), eq(5), eq(100)))
                .thenReturn(List.of(first, second));
        doThrow(failure).when(processor).process(first);

        worker.processPendingEvents();

        verify(stateService).markFailed(eq(first), same(failure), eq(5), any(LocalDateTime.class));
        verify(processor).process(second);
    }

    @Test
    void ownershipLossDoesNotOverwriteEventAsFailed() {
        ClaimedOutboxEvent claim = claim(1L, OutboxEventType.ORDER_PAID);
        when(stateService.claimBatch(any(), any(), eq(5), eq(100)))
                .thenReturn(List.of(claim));
        doThrow(new OutboxEventClaimOwnershipLostException(claim.eventId(), claim.claimToken()))
                .when(processor).process(claim);

        worker.processPendingEvents();

        verify(stateService, never()).markFailed(any(), any(), anyInt(), any());
    }

    private ClaimedOutboxEvent claim(Long id, OutboxEventType type) {
        return new ClaimedOutboxEvent(id, UUID.randomUUID(), type);
    }
}
