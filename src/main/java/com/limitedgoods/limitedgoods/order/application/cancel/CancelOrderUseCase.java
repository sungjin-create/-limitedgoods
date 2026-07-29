package com.limitedgoods.limitedgoods.order.application.cancel;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.cancel.dto.RefundStartResult;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.payment.dto.RefundLookupResult;
import com.limitedgoods.limitedgoods.payment.dto.RefundResult;
import com.limitedgoods.limitedgoods.payment.exception.PaymentNetworkException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentRefundDeclinedException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentTimeoutException;
import com.limitedgoods.limitedgoods.payment.service.PaymentService;
import com.limitedgoods.limitedgoods.payment.service.RefundAttemptStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final OrderCancellationService cancellationService;
    private final PaymentService paymentService;
    private final RefundAttemptStateService refundAttemptStateService;


    public OrderResponse execute(
            Long userId,
            Long orderId
    ) {
        RefundStartResult start = cancellationService.prepareRefund(userId, orderId);

        return switch (start.action()) {
            case RETURN_REFUNDED -> start.completedOrder();

            case FINALIZE_APPROVED ->
                    cancellationService.completeRefund(
                            start.userId(),
                            start.orderId()
                    );

            case REQUEST_PG -> requestRefund(start);

            case RECONCILE_PG -> reconcileRefund(start);
        };
    }

    private OrderResponse requestRefund(RefundStartResult start) {
        try {
            RefundResult result = paymentService.cancel(
                    start.pgTransactionId(),
                    start.amount(),
                    start.idempotencyKey()
            );

            return applyApproved(start, result);

        } catch (PaymentRefundDeclinedException exception) {
            refundAttemptStateService.decline(
                    start.refundAttemptId(),
                    "PG_REFUND_DECLINED",
                    exception.getMessage()
            );

            cancellationService.failRefund(
                    start.userId(),
                    start.orderId(),
                    exception.getMessage()
            );

            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);

        } catch (PaymentTimeoutException | PaymentNetworkException exception) {
            refundAttemptStateService.markUnknown(
                    start.refundAttemptId(),
                    exception.getMessage()
            );

            throw new BusinessException(ErrorCode.PAYMENT_REFUND_RESULT_UNKNOWN);
        }
    }

    private OrderResponse reconcileRefund(RefundStartResult start) {
        RefundLookupResult lookup = paymentService.lookupRefund(
                start.pgTransactionId(),
                start.idempotencyKey()
        );

        return switch (lookup.status()) {
            case APPROVED -> applyApproved(start, lookup.toRefundResult());

            case DECLINED -> {
                refundAttemptStateService.decline(
                        start.refundAttemptId(),
                        lookup.failureCode(),
                        lookup.failureReason()
                );

                cancellationService.failRefund(
                        start.userId(),
                        start.orderId(),
                        lookup.failureReason()
                );

                throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED);
            }

            case PROCESSING, NOT_FOUND -> {
                refundAttemptStateService.markUnknown(
                        start.refundAttemptId(),
                        "PG 환불 결과 확인 중"
                );

                throw new BusinessException(ErrorCode.PAYMENT_REFUND_PROCESSING);
            }
        };
    }

    private OrderResponse applyApproved(
            RefundStartResult start,
            RefundResult result
    ) {
        boolean approved = refundAttemptStateService.approve(
                start.refundAttemptId(),
                result
        );

        if (!approved) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        return cancellationService.completeRefund(
                start.userId(),
                start.orderId()
        );
    }
}