package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.payment.dto.PaymentLookupResult;
import com.limitedgoods.limitedgoods.payment.dto.PaymentLookupStatus;
import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import com.limitedgoods.limitedgoods.payment.exception.PaymentDeclinedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class FakePaymentService implements PaymentService {

    private final ConcurrentMap<String, PaymentLookupResult> results = new ConcurrentHashMap<>();

    @Override
    public PaymentResult pay(
            Long orderId,
            long amount,
            String idempotencyKey,
            PaymentRequest request
    ) {
        String key = key(orderId, idempotencyKey);

        PaymentLookupResult existing = results.get(key);

        if (existing != null) {
            if (existing.status() == PaymentLookupStatus.APPROVED) {
                return existing.toPaymentResult();
            }

            if (existing.status() == PaymentLookupStatus.DECLINED) {
                throw new PaymentDeclinedException(
                        existing.failureCode(),
                        existing.failureReason()
                );
            }
        }

        if (request.isForceFail()) {
            PaymentLookupResult declined = new PaymentLookupResult(
                    PaymentLookupStatus.DECLINED,
                    null,
                    0,
                    null,
                    "FAKE_DECLINED",
                    "결제 승인 실패"
            );

            results.putIfAbsent(key, declined);

            throw new PaymentDeclinedException(
                    "FAKE_DECLINED",
                    "결제 승인 실패"
            );
        }

        PaymentLookupResult approved = new PaymentLookupResult(
                PaymentLookupStatus.APPROVED,
                UUID.randomUUID().toString(),
                amount,
                LocalDateTime.now(),
                null,
                null
        );

        PaymentLookupResult saved = results.putIfAbsent(key, approved);

        PaymentLookupResult finalResult = saved == null ? approved : saved;

        return finalResult.toPaymentResult();
    }

    @Override
    public PaymentLookupResult lookup(Long orderId, String idempotencyKey) {
        return results.getOrDefault(
                key(orderId, idempotencyKey),
                new PaymentLookupResult(
                        PaymentLookupStatus.NOT_FOUND,
                        null,
                        0,
                        null,
                        null,
                        null
                )
        );
    }

    @Override
    public void cancel(
            String pgTransactionId,
            long amount,
            String idempotencyKey
    ) {
        // Fake 구현에서는 취소 성공으로 처리
    }

    private String key(Long orderId, String idempotencyKey) {
        return orderId + ":" + idempotencyKey;
    }
}