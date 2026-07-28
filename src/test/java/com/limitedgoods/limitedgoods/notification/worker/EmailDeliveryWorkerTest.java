package com.limitedgoods.limitedgoods.notification.worker;

import com.limitedgoods.limitedgoods.notification.exception.EmailInfrastructureException;
import com.limitedgoods.limitedgoods.notification.infrastructure.mail.EmailProviderCircuit;
import com.limitedgoods.limitedgoods.notification.service.ClaimedEmailDelivery;
import com.limitedgoods.limitedgoods.notification.service.EmailDeliveryProcessor;
import com.limitedgoods.limitedgoods.notification.service.EmailDeliveryStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryWorkerTest {

    @Mock
    private EmailDeliveryStateService stateService;

    @Mock
    private EmailDeliveryProcessor deliveryService;

    @Mock
    private EmailProviderCircuit providerCircuit;

    private EmailDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new EmailDeliveryWorker(
                stateService,
                deliveryService,
                providerCircuit
        );

        ReflectionTestUtils.setField(worker, "maxAttempts", 5);
        ReflectionTestUtils.setField(
                worker,
                "processingLeaseSeconds",
                600L
        );
        ReflectionTestUtils.setField(worker, "batchSize", 10);
        ReflectionTestUtils.setField(
                worker,
                "infrastructureBackoffMs",
                300_000L
        );
    }

    @Test
    @DisplayName("Circuit이 열려 있으면 이메일을 가져오지 않는다")
    void doNotClaimWhenCircuitIsOpen() {
        when(providerCircuit.isBlocked(any(Instant.class)))
                .thenReturn(true);

        worker.sendPendingEmails();

        verifyNoInteractions(stateService);
        verifyNoInteractions(deliveryService);
    }

    @Test
    @DisplayName("claim한 이메일을 순서대로 발송한다")
    void sendClaimedEmailsInOrder() {
        when(providerCircuit.isBlocked(any(Instant.class)))
                .thenReturn(false);

        ClaimedEmailDelivery first = claim(1L);
        ClaimedEmailDelivery second = claim(2L);

        when(stateService.claimBatch(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5),
                eq(10)
        )).thenReturn(List.of(first, second));

        worker.sendPendingEmails();

        InOrder inOrder = inOrder(deliveryService);
        inOrder.verify(deliveryService).process(first);
        inOrder.verify(deliveryService).process(second);
    }

    @Test
    @DisplayName("인프라 장애가 발생하면 나머지 claim을 반환하고 배치를 중단한다")
    void releaseRemainingClaimsAfterInfrastructureFailure() {
        when(providerCircuit.isBlocked(any(Instant.class)))
                .thenReturn(false);

        ClaimedEmailDelivery first = claim(1L);
        ClaimedEmailDelivery second = claim(2L);
        ClaimedEmailDelivery third = claim(3L);

        when(stateService.claimBatch(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5),
                eq(10)
        )).thenReturn(List.of(first, second, third));

        EmailInfrastructureException exception =
                new EmailInfrastructureException(
                        "SMTP authentication failed",
                        new RuntimeException()
                );

        doThrow(exception)
                .when(deliveryService)
                .process(first);

        worker.sendPendingEmails();

        verify(deliveryService).process(first);
        verify(deliveryService, never()).process(second);
        verify(deliveryService, never()).process(third);

        verify(stateService)
                .releaseBatchAfterInfrastructureFailure(
                        eq(List.of(second, third)),
                        contains("EmailInfrastructureException"),
                        any(LocalDateTime.class)
                );
    }

    @Test
    @DisplayName("일반 예외가 발생해도 다음 이메일 처리를 계속한다")
    void continueAfterUnexpectedFailure() {
        when(providerCircuit.isBlocked(any(Instant.class)))
                .thenReturn(false);

        ClaimedEmailDelivery first = claim(1L);
        ClaimedEmailDelivery second = claim(2L);

        when(stateService.claimBatch(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5),
                eq(10)
        )).thenReturn(List.of(first, second));

        doThrow(new RuntimeException("unexpected error"))
                .when(deliveryService)
                .process(first);

        worker.sendPendingEmails();

        verify(deliveryService).process(first);
        verify(deliveryService).process(second);

        verify(
                stateService,
                never()
        ).releaseBatchAfterInfrastructureFailure(
                anyList(),
                anyString(),
                any(LocalDateTime.class)
        );
    }

    private ClaimedEmailDelivery claim(Long deliveryId) {
        return new ClaimedEmailDelivery(
                deliveryId,
                UUID.randomUUID()
        );
    }
}
