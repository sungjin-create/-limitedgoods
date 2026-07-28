package com.limitedgoods.limitedgoods.order.application.payment;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartAction;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartResult;
import com.limitedgoods.limitedgoods.order.application.payment.idempotency.OrderPaymentIdempotencyService;
import com.limitedgoods.limitedgoods.order.application.payment.idempotency.PaymentRequestFingerprintGenerator;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.payment.dto.*;
import com.limitedgoods.limitedgoods.payment.exception.PaymentDeclinedException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentTimeoutException;
import com.limitedgoods.limitedgoods.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderUseCaseTest {

    private static final long USER_ID = 1L;
    private static final long ORDER_ID = 10L;
    private static final long ATTEMPT_ID = 100L;
    private static final String KEY = "payment-key-0001";
    private static final String FINGERPRINT = "fingerprint";

    @Mock ApprovedPaymentFinalizer finalizer;
    @Mock PaymentRequestFingerprintGenerator fingerprintGenerator;
    @Mock PaymentCommandService commandService;
    @Mock PaymentService paymentService;
    @Mock OrderPaymentIdempotencyService idempotencyService;

    private PayOrderUseCase useCase;
    private PaymentRequest request;

    @BeforeEach
    void setUp() {
        useCase = new PayOrderUseCase(
                finalizer,
                fingerprintGenerator,
                commandService,
                paymentService,
                idempotencyService
        );
        request = new PaymentRequest();
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeAnyStateChange() {
        assertBusinessError(
                () -> useCase.execute(USER_ID, ORDER_ID, request, "short"),
                ErrorCode.INVALID_PAYMENT_IDEMPOTENCY_KEY
        );

        verifyNoInteractions(commandService, paymentService, finalizer, idempotencyService);
    }

    @Test
    void cachedSuccessResponseSkipsDatabaseAndGateway() {
        OrderResponse cached = response();
        when(idempotencyService.getSavedResponse(USER_ID, ORDER_ID, KEY)).thenReturn(cached);

        OrderResponse result = useCase.execute(USER_ID, ORDER_ID, request, KEY);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(commandService, paymentService, finalizer, fingerprintGenerator);
    }

    @Test
    void approvedOrderIsFinalizedWithoutCallingGateway() {
        stubStart(new PaymentStartResult(
                PaymentStartAction.FINALIZE_APPROVED,
                ORDER_ID, 10_000L, null, null, null
        ));
        OrderResponse paid = response();
        when(finalizer.finalizePayment(USER_ID, ORDER_ID)).thenReturn(paid);

        OrderResponse result = useCase.execute(USER_ID, ORDER_ID, request, KEY);

        assertThat(result).isSameAs(paid);
        verifyNoInteractions(paymentService);
        verify(idempotencyService).saveResponse(USER_ID, ORDER_ID, KEY, paid);
    }

    @Test
    void newPaymentApprovalIsRecordedAndFinalized() {
        stubStart(requestPg());
        PaymentResult pgResult = new PaymentResult("pg-1", 10_000L, LocalDateTime.now());
        when(paymentService.pay(ORDER_ID, 10_000L, KEY, request)).thenReturn(pgResult);
        when(commandService.recordApproval(USER_ID, ORDER_ID, ATTEMPT_ID, pgResult)).thenReturn(true);
        OrderResponse paid = response();
        when(finalizer.finalizePayment(USER_ID, ORDER_ID)).thenReturn(paid);

        OrderResponse result = useCase.execute(USER_ID, ORDER_ID, request, KEY);

        assertThat(result).isSameAs(paid);
        verify(commandService).recordApproval(USER_ID, ORDER_ID, ATTEMPT_ID, pgResult);
        verify(finalizer).finalizePayment(USER_ID, ORDER_ID);
        verify(idempotencyService).saveResponse(USER_ID, ORDER_ID, KEY, paid);
    }

    @Test
    void amountMismatchStopsBeforeInternalFinalization() {
        stubStart(requestPg());
        PaymentResult pgResult = new PaymentResult("pg-2", 9_000L, LocalDateTime.now());
        when(paymentService.pay(ORDER_ID, 10_000L, KEY, request)).thenReturn(pgResult);
        when(commandService.recordApproval(USER_ID, ORDER_ID, ATTEMPT_ID, pgResult)).thenReturn(false);

        assertBusinessError(
                () -> useCase.execute(USER_ID, ORDER_ID, request, KEY),
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
        );

        verifyNoInteractions(finalizer);
    }

    @Test
    void gatewayDeclineIsPersistedAsPaymentFailure() {
        stubStart(requestPg());
        when(paymentService.pay(ORDER_ID, 10_000L, KEY, request))
                .thenThrow(new PaymentDeclinedException("DECLINED", "card declined"));

        assertBusinessError(
                () -> useCase.execute(USER_ID, ORDER_ID, request, KEY),
                ErrorCode.PAYMENT_FAILED
        );

        verify(commandService).recordDecline(
                USER_ID, ORDER_ID, ATTEMPT_ID, "DECLINED", "card declined"
        );
    }

    @Test
    void gatewayTimeoutIsMarkedUnknownForReconciliation() {
        stubStart(requestPg());
        when(paymentService.pay(ORDER_ID, 10_000L, KEY, request))
                .thenThrow(new PaymentTimeoutException("timeout"));

        assertBusinessError(
                () -> useCase.execute(USER_ID, ORDER_ID, request, KEY),
                ErrorCode.PAYMENT_RESULT_UNKNOWN
        );

        verify(commandService).recordUnknown(ATTEMPT_ID, "timeout");
    }

    @Test
    void unknownAttemptIsReconciledAndFinalizedWhenGatewayReportsApproval() {
        stubStart(new PaymentStartResult(
                PaymentStartAction.RECONCILE_PG,
                ORDER_ID, 10_000L, ATTEMPT_ID, KEY, null
        ));
        PaymentLookupResult lookup = new PaymentLookupResult(
                PaymentLookupStatus.APPROVED,
                "pg-3", 10_000L, LocalDateTime.now(), null, null
        );
        when(paymentService.lookup(ORDER_ID, KEY)).thenReturn(lookup);
        when(commandService.recordApproval(
                eq(USER_ID), eq(ORDER_ID), eq(ATTEMPT_ID), any(PaymentResult.class)
        )).thenReturn(true);
        when(finalizer.finalizePayment(USER_ID, ORDER_ID)).thenReturn(response());

        useCase.execute(USER_ID, ORDER_ID, request, KEY);

        verify(paymentService).lookup(ORDER_ID, KEY);
        verify(finalizer).finalizePayment(USER_ID, ORDER_ID);
    }

    private void stubStart(PaymentStartResult start) {
        when(fingerprintGenerator.generate(ORDER_ID, request)).thenReturn(FINGERPRINT);
        when(commandService.preparePayment(USER_ID, ORDER_ID, KEY, FINGERPRINT)).thenReturn(start);
    }

    private PaymentStartResult requestPg() {
        return new PaymentStartResult(
                PaymentStartAction.REQUEST_PG,
                ORDER_ID, 10_000L, ATTEMPT_ID, KEY, null
        );
    }

    private OrderResponse response() {
        return OrderResponse.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .totalPrice(10_000L)
                .status("PAID")
                .build();
    }

    private void assertBusinessError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
