package com.limitedgoods.limitedgoods.order.application.create;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.create.dto.OrderAdmissionClaim;
import com.limitedgoods.limitedgoods.queue.config.QueueAdmissionProperties;
import com.limitedgoods.limitedgoods.queue.service.AdmissionTokenService;
import com.limitedgoods.limitedgoods.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderAdmissionCoordinatorTest {

    private static final long GENERATION = 3L;

    @Mock AdmissionTokenService admissionTokenService;
    @Mock QueueService queueService;

    private OrderAdmissionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new OrderAdmissionCoordinator(
                admissionTokenService,
                queueService,
                admissionProperties(false)
        );
    }

    @Test
    void normalProductDoesNotRequireAdmissionToken() {
        Optional<OrderAdmissionClaim> result = coordinator.claimAdmissionIfRequired(
                null, 1L, null, "checkout", "fingerprint"
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(admissionTokenService, queueService);
    }

    @Test
    void limitedProductRequiresAdmissionToken() {
        assertBusinessError(
                () -> coordinator.claimAdmissionIfRequired(null, 1L, 10L, "checkout", "fingerprint"),
                ErrorCode.ADMISSION_TOKEN_REQUIRED
        );
    }

    @Test
    void loadTestBypassSkipsAdmissionForLimitedProduct() {
        coordinator = new OrderAdmissionCoordinator(
                admissionTokenService,
                queueService,
                admissionProperties(true)
        );

        Optional<OrderAdmissionClaim> result = coordinator.claimAdmissionIfRequired(
                null, 1L, 10L, "checkout", "fingerprint"
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(admissionTokenService, queueService);
    }

    @Test
    void invalidAdmissionTokenIsRejected() {
        when(admissionTokenService.claimToken("token", 1L, 10L, "checkout:fingerprint"))
                .thenReturn(null);

        assertBusinessError(
                () -> coordinator.claimAdmissionIfRequired("token", 1L, 10L, "checkout", "fingerprint"),
                ErrorCode.ADMISSION_TOKEN_INVALID
        );
    }

    @Test
    void validAdmissionTokenReturnsClaimWithDeterministicClaimId() {
        when(admissionTokenService.claimToken("token", 1L, 10L, "checkout:fingerprint"))
                .thenReturn(GENERATION);

        OrderAdmissionClaim claim = coordinator.claimAdmissionIfRequired(
                "token", 1L, 10L, "checkout", "fingerprint"
        ).orElseThrow();

        assertThat(claim.claimId()).isEqualTo("checkout:fingerprint");
        assertThat(claim.userId()).isEqualTo(1L);
        assertThat(claim.productId()).isEqualTo(10L);
        assertThat(claim.generation()).isEqualTo(GENERATION);
    }

    @Test
    void successfulOrderRemovesQueueBeforeConsumingToken() {
        OrderAdmissionClaim claim = claim();
        when(admissionTokenService.completeTokenConsumption(
                "token", 1L, 10L, "claim", GENERATION
        ))
                .thenReturn(true);

        coordinator.completeClaimAfterOrderCreated(Optional.of(claim));

        InOrder inOrder = inOrder(queueService, admissionTokenService);
        inOrder.verify(queueService).removeFromQueue(1L, 10L);
        inOrder.verify(admissionTokenService)
                .completeTokenConsumption("token", 1L, 10L, "claim", GENERATION);
    }

    @Test
    void queueRemovalFailureKeepsTokenTrackingState() {
        OrderAdmissionClaim claim = claim();
        doThrow(new RuntimeException("redis unavailable"))
                .when(queueService).removeFromQueue(1L, 10L);

        coordinator.completeClaimAfterOrderCreated(Optional.of(claim));

        verifyNoInteractions(admissionTokenService);
    }

    @Test
    void businessFailureReleasesClaimWithRemainingTtl() {
        OrderAdmissionClaim claim = claim();
        when(admissionTokenService.releaseClaim("token", 1L, 10L, "claim", GENERATION))
                .thenReturn(true);

        coordinator.releaseClaimAfterBusinessFailure(Optional.of(claim));

        verify(admissionTokenService)
                .releaseClaim("token", 1L, 10L, "claim", GENERATION);
    }

    private OrderAdmissionClaim claim() {
        return new OrderAdmissionClaim("token", 1L, 10L, "claim", GENERATION);
    }

    private QueueAdmissionProperties admissionProperties(boolean bypassEnabled) {
        QueueAdmissionProperties properties = new QueueAdmissionProperties();
        properties.setBypassEnabled(bypassEnabled);
        return properties;
    }

    private void assertBusinessError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
