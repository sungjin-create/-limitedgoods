package com.limitedgoods.limitedgoods.order.application.payment;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartResult;
import com.limitedgoods.limitedgoods.order.application.payment.idempotency.OrderPaymentIdempotencyService;
import com.limitedgoods.limitedgoods.order.application.payment.idempotency.PaymentRequestFingerprintGenerator;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.payment.dto.PaymentLookupResult;
import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import com.limitedgoods.limitedgoods.payment.exception.PaymentDeclinedException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentNetworkException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentTimeoutException;
import com.limitedgoods.limitedgoods.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderUseCase {

    private final ApprovedPaymentFinalizer paymentFinalizer;
    private final PaymentRequestFingerprintGenerator fingerprintGenerator;
    private final PaymentCommandService paymentCommandService;
    private final PaymentService paymentService;
    private final OrderPaymentIdempotencyService orderPaymentIdempotencyService;

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,99}$");

    public OrderResponse execute(
            Long userId,
            Long orderId,
            PaymentRequest request,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);

        OrderResponse cachedResponse =
                orderPaymentIdempotencyService.getSavedResponse(
                        userId,
                        orderId,
                        idempotencyKey
                );

        if (cachedResponse != null) {
            return cachedResponse;
        }

        String fingerprint = fingerprintGenerator.generate(orderId, request);

        PaymentStartResult start =
                paymentCommandService.preparePayment(
                        userId,
                        orderId,
                        idempotencyKey,
                        fingerprint
                );

        return switch (start.action()) {
            case RETURN_PAID ->
                    completeSuccess(
                            userId,
                            orderId,
                            idempotencyKey,
                            start.completedOrder()
                    );

            case FINALIZE_APPROVED -> {
                OrderResponse response =
                        paymentFinalizer.finalizePayment(userId, orderId);

                yield completeSuccess(
                        userId,
                        orderId,
                        idempotencyKey,
                        response
                );
            }

            case RECONCILE_PG -> {
                OrderResponse response =
                        reconcilePayment(userId, start);

                yield completeSuccess(
                        userId,
                        orderId,
                        idempotencyKey,
                        response
                );
            }

            case REQUEST_PG ->
                    requestAndCompletePayment(
                            userId,
                            request,
                            start,
                            idempotencyKey
                    );
        };
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_PAYMENT_IDEMPOTENCY_KEY
            );
        }
    }

    private OrderResponse completeSuccess(
            Long userId,
            Long orderId,
            String idempotencyKey,
            OrderResponse response
    ) {
        saveResponseBestEffort(
                userId,
                orderId,
                idempotencyKey,
                response
        );

        return response;
    }

    private void saveResponseBestEffort(
            Long userId,
            Long orderId,
            String idempotencyKey,
            OrderResponse response
    ) {
        try {
            orderPaymentIdempotencyService.saveResponse(
                    userId,
                    orderId,
                    idempotencyKey,
                    response
            );
        } catch (Exception exception) {
            /*
             * DB 주문 상태는 이미 PAID다.
             * Redis 응답 저장 실패가 결제 성공을 실패 응답으로
             * 바꾸지 않도록 예외를 격리한다.
             */
            log.error(
                    "[결제 멱등 응답 저장 실패] userId={}, orderId={}",
                    userId,
                    orderId,
                    exception
            );
        }
    }


    private OrderResponse reconcilePayment(
            Long userId,
            PaymentStartResult start
    ) {
        PaymentLookupResult lookup = paymentService.lookup(
                start.orderId(),
                start.idempotencyKey()
        );

        return switch (lookup.status()) {
            case APPROVED -> {
                boolean approved =
                        paymentCommandService.recordApproval(
                                userId,
                                start.orderId(),
                                start.paymentAttemptId(),
                                lookup.toPaymentResult()
                        );

                if (!approved) {
                    throw new BusinessException(
                            ErrorCode.PAYMENT_AMOUNT_MISMATCH
                    );
                }

                yield paymentFinalizer.finalizePayment(
                        userId,
                        start.orderId()
                );
            }

            case DECLINED -> {
                paymentCommandService.recordDecline(
                        userId,
                        start.orderId(),
                        start.paymentAttemptId(),
                        lookup.failureCode(),
                        lookup.failureReason()
                );

                throw new BusinessException(
                        ErrorCode.PAYMENT_FAILED
                );
            }

            case PROCESSING, NOT_FOUND -> {
                paymentCommandService.recordUnknown(
                        start.paymentAttemptId(),
                        "PG 결제 결과 확인 중"
                );

                throw new BusinessException(
                        ErrorCode.PAYMENT_PROCESSING
                );
            }
        };
    }

    private OrderResponse requestAndCompletePayment(
            Long userId,
            PaymentRequest request,
            PaymentStartResult start,
            String idempotencyKey
    ) {
        try {
            PaymentResult result = paymentService.pay(
                    start.orderId(),
                    start.amount(),
                    start.idempotencyKey(),
                    request
            );

            boolean approved =
                    paymentCommandService.recordApproval(
                            userId,
                            start.orderId(),
                            start.paymentAttemptId(),
                            result
                    );

            if (!approved) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_AMOUNT_MISMATCH
                );
            }

            OrderResponse response =
                    paymentFinalizer.finalizePayment(
                            userId,
                            start.orderId()
                    );

            return completeSuccess(
                    userId,
                    start.orderId(),
                    idempotencyKey,
                    response
            );

        } catch (PaymentDeclinedException exception) {
            paymentCommandService.recordDecline(
                    userId,
                    start.orderId(),
                    start.paymentAttemptId(),
                    exception.getFailureCode(),
                    exception.getMessage()
            );

            throw new BusinessException(ErrorCode.PAYMENT_FAILED);

        } catch (PaymentTimeoutException | PaymentNetworkException exception) {
            paymentCommandService.recordUnknown(
                    start.paymentAttemptId(),
                    exception.getMessage()
            );

            throw new BusinessException(
                    ErrorCode.PAYMENT_RESULT_UNKNOWN
            );
        }
    }
}