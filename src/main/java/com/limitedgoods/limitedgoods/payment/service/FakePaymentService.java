package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.payment.dto.*;
import com.limitedgoods.limitedgoods.payment.exception.PaymentDeclinedException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentNetworkException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentRefundDeclinedException;
import com.limitedgoods.limitedgoods.payment.exception.PaymentTimeoutException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class FakePaymentService implements PaymentService {

    /*
     * 결제 요청 결과:
     * key = orderId:idempotencyKey
     */
    private final ConcurrentMap<String, PaymentLookupResult>
            paymentResults = new ConcurrentHashMap<>();

    /*
     * 환불 대상 거래를 검증하기 위한 인덱스:
     * key = PG 결제 거래 ID
     */
    private final ConcurrentMap<String, PaymentLookupResult>
            approvedPaymentsByTransactionId = new ConcurrentHashMap<>();

    /*
     * 환불 결과:
     * key = pgTransactionId:idempotencyKey
     */
    private final ConcurrentMap<String, RefundLookupResult>
            refundResults = new ConcurrentHashMap<>();

    /*
     * 동일 멱등 키를 다른 금액으로 재사용하는 것을 방지한다.
     */
    private final ConcurrentMap<String, RefundRequestFingerprint>
            refundRequests = new ConcurrentHashMap<>();

    /*
     * 개발·테스트에서 환불 타임아웃 등을 재현하기 위한 설정.
     * key = pgTransactionId
     */
    private final ConcurrentMap<String, FakeRefundScenario>
            refundScenarios = new ConcurrentHashMap<>();

    @Override
    public PaymentResult pay(
            Long orderId,
            long amount,
            String idempotencyKey,
            PaymentRequest request
    ) {
        String key = paymentKey(orderId, idempotencyKey);

        PaymentLookupResult existing = paymentResults.get(key);

        if (existing != null) {
            return resolveExistingPayment(existing);
        }

        if (request.isForceFail()) {
            PaymentLookupResult declined = new PaymentLookupResult(
                    PaymentLookupStatus.DECLINED,
                    null,
                    0,
                    null,
                    "FAKE_DECLINED",
                    "결제 승인이 거절되었습니다."
            );

            PaymentLookupResult saved =
                    paymentResults.putIfAbsent(key, declined);

            PaymentLookupResult finalResult =
                    saved == null ? declined : saved;

            return resolveExistingPayment(finalResult);
        }

        PaymentLookupResult approved = new PaymentLookupResult(
                PaymentLookupStatus.APPROVED,
                UUID.randomUUID().toString(),
                amount,
                LocalDateTime.now(),
                null,
                null
        );

        PaymentLookupResult saved =
                paymentResults.putIfAbsent(key, approved);

        PaymentLookupResult finalResult =
                saved == null ? approved : saved;

        if (finalResult.status() == PaymentLookupStatus.APPROVED) {
            approvedPaymentsByTransactionId.putIfAbsent(
                    finalResult.transactionId(),
                    finalResult
            );
        }

        return resolveExistingPayment(finalResult);
    }

    @Override
    public PaymentLookupResult lookup(
            Long orderId,
            String idempotencyKey
    ) {
        return paymentResults.getOrDefault(
                paymentKey(orderId, idempotencyKey),
                paymentNotFound()
        );
    }

    @Override
    public RefundResult cancel(
            String pgTransactionId,
            long amount,
            String idempotencyKey
    ) {
        validateRefundRequest(
                pgTransactionId,
                amount,
                idempotencyKey
        );

        String key = refundKey(
                pgTransactionId,
                idempotencyKey
        );

        RefundLookupResult existing = refundResults.get(key);

        if (existing != null) {
            return resolveExistingRefund(existing);
        }

        FakeRefundScenario scenario =
                refundScenarios.getOrDefault(
                        pgTransactionId,
                        FakeRefundScenario.APPROVE
                );

        return switch (scenario) {
            case APPROVE ->
                    approveRefund(key, amount);

            /*
             * PG에서는 환불됐지만 응답이 클라이언트에 도착하지 않은 상황.
             * 결과는 APPROVED로 저장하고 타임아웃 예외를 던진다.
             */
            case APPROVE_THEN_TIMEOUT -> {
                approveRefund(key, amount);

                throw new PaymentTimeoutException(
                        "PG 환불은 처리됐지만 응답이 타임아웃되었습니다."
                );
            }

            /*
             * PG가 아직 환불을 처리 중인 상황.
             */
            case PROCESSING -> {
                refundResults.putIfAbsent(
                        key,
                        processingRefund(amount)
                );

                throw new PaymentTimeoutException(
                        "PG에서 환불을 처리하고 있습니다."
                );
            }

            /*
             * PG가 명시적으로 환불을 거절한 상황.
             */
            case DECLINE -> {
                RefundLookupResult declined = declinedRefund(
                        "FAKE_REFUND_DECLINED",
                        "PG에서 환불 요청을 거절했습니다."
                );

                refundResults.putIfAbsent(key, declined);

                throw new PaymentRefundDeclinedException(
                        declined.failureReason()
                );
            }

            /*
             * PG 요청 자체가 도달하지 않은 상황.
             * PG 결과도 저장하지 않는다.
             */
            case NETWORK_ERROR -> throw new PaymentNetworkException(
                    "PG 환불 요청 중 네트워크 오류가 발생했습니다."
            );
        };
    }

    @Override
    public RefundLookupResult lookupRefund(
            String pgTransactionId,
            String idempotencyKey
    ) {
        return refundResults.getOrDefault(
                refundKey(pgTransactionId, idempotencyKey),
                refundNotFound()
        );
    }

    private PaymentResult resolveExistingPayment(
            PaymentLookupResult result
    ) {
        return switch (result.status()) {
            case APPROVED -> result.toPaymentResult();

            case DECLINED -> throw new PaymentDeclinedException(
                    result.failureCode(),
                    result.failureReason()
            );

            case PROCESSING, NOT_FOUND -> throw new PaymentTimeoutException(
                    "결제 결과가 아직 확정되지 않았습니다."
            );
        };
    }

    private RefundResult resolveExistingRefund(
            RefundLookupResult result
    ) {
        return switch (result.status()) {
            case APPROVED -> result.toRefundResult();

            case DECLINED ->
                    throw new PaymentRefundDeclinedException(
                            result.failureReason()
                    );

            case PROCESSING, NOT_FOUND ->
                    throw new PaymentTimeoutException(
                            "환불 결과가 아직 확정되지 않았습니다."
                    );
        };
    }

    private RefundResult approveRefund(
            String refundKey,
            long amount
    ) {
        RefundLookupResult approved = new RefundLookupResult(
                RefundLookupStatus.APPROVED,
                UUID.randomUUID().toString(),
                amount,
                LocalDateTime.now(),
                null,
                null
        );

        RefundLookupResult saved =
                refundResults.putIfAbsent(refundKey, approved);

        RefundLookupResult finalResult =
                saved == null ? approved : saved;

        return resolveExistingRefund(finalResult);
    }

    private void validateRefundRequest(
            String pgTransactionId,
            long amount,
            String idempotencyKey
    ) {
        if (pgTransactionId == null
                || pgTransactionId.isBlank()) {
            throw new PaymentRefundDeclinedException(
                    "PG 결제 거래 ID가 없습니다."
            );
        }

        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new PaymentRefundDeclinedException(
                    "환불 멱등 키가 없습니다."
            );
        }

        PaymentLookupResult payment =
                approvedPaymentsByTransactionId.get(
                        pgTransactionId
                );

        if (payment == null
                || payment.status()
                != PaymentLookupStatus.APPROVED) {
            throw new PaymentRefundDeclinedException(
                    "승인된 결제 거래를 찾을 수 없습니다."
            );
        }

        /*
         * 현재 프로젝트는 전액 환불만 지원하므로
         * 실제 승인 금액과 환불 요청 금액이 같아야 한다.
         */
        if (payment.approvedAmount() != amount) {
            throw new PaymentRefundDeclinedException(
                    "승인 금액과 환불 요청 금액이 일치하지 않습니다."
            );
        }

        String key = refundKey(
                pgTransactionId,
                idempotencyKey
        );

        RefundRequestFingerprint requested =
                new RefundRequestFingerprint(
                        pgTransactionId,
                        amount
                );

        RefundRequestFingerprint existing =
                refundRequests.putIfAbsent(
                        key,
                        requested
                );

        if (existing != null
                && !Objects.equals(existing, requested)) {
            throw new PaymentRefundDeclinedException(
                    "동일한 환불 멱등 키가 다른 요청에 사용됐습니다."
            );
        }
    }

    /*
     * PROCESSING 시나리오를 나중에 APPROVED로 변경한다.
     * 통합 테스트에서 스케줄러 복구를 검증할 때 사용한다.
     */
    public void approvePendingRefund(
            String pgTransactionId,
            String idempotencyKey
    ) {
        String key = refundKey(
                pgTransactionId,
                idempotencyKey
        );

        RefundRequestFingerprint request =
                refundRequests.get(key);

        if (request == null) {
            throw new IllegalStateException(
                    "환불 요청을 찾을 수 없습니다."
            );
        }

        refundResults.put(
                key,
                new RefundLookupResult(
                        RefundLookupStatus.APPROVED,
                        UUID.randomUUID().toString(),
                        request.amount(),
                        LocalDateTime.now(),
                        null,
                        null
                )
        );
    }

    public void declinePendingRefund(
            String pgTransactionId,
            String idempotencyKey
    ) {
        String key = refundKey(
                pgTransactionId,
                idempotencyKey
        );

        refundResults.put(
                key,
                declinedRefund(
                        "FAKE_REFUND_DECLINED",
                        "PG에서 환불 요청을 거절했습니다."
                )
        );
    }

    /*
     * 테스트 시작 전에 환불 시나리오를 설정한다.
     */
    public void setRefundScenario(
            String pgTransactionId,
            FakeRefundScenario scenario
    ) {
        refundScenarios.put(
                pgTransactionId,
                scenario
        );
    }

    public void clearRefundScenario(
            String pgTransactionId
    ) {
        refundScenarios.remove(pgTransactionId);
    }

    private RefundLookupResult processingRefund(long amount) {
        return new RefundLookupResult(
                RefundLookupStatus.PROCESSING,
                null,
                amount,
                null,
                null,
                "PG 환불 처리 중"
        );
    }

    private RefundLookupResult declinedRefund(
            String code,
            String reason
    ) {
        return new RefundLookupResult(
                RefundLookupStatus.DECLINED,
                null,
                0,
                null,
                code,
                reason
        );
    }

    private PaymentLookupResult paymentNotFound() {
        return new PaymentLookupResult(
                PaymentLookupStatus.NOT_FOUND,
                null,
                0,
                null,
                null,
                null
        );
    }

    private RefundLookupResult refundNotFound() {
        return new RefundLookupResult(
                RefundLookupStatus.NOT_FOUND,
                null,
                0,
                null,
                null,
                null
        );
    }

    private String paymentKey(
            Long orderId,
            String idempotencyKey
    ) {
        return orderId + ":" + idempotencyKey;
    }

    private String refundKey(
            String pgTransactionId,
            String idempotencyKey
    ) {
        return pgTransactionId + ":" + idempotencyKey;
    }

    public enum FakeRefundScenario {
        APPROVE,
        APPROVE_THEN_TIMEOUT,
        PROCESSING,
        DECLINE,
        NETWORK_ERROR
    }

    private record RefundRequestFingerprint(
            String pgTransactionId,
            long amount
    ) {
    }
}