package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.payment.dto.PaymentLookupResult;
import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;

public interface PaymentService {

    PaymentResult pay(
            Long orderId,
            long amount,
            String idempotencyKey,
            PaymentRequest request
    );

    PaymentLookupResult lookup(
            Long orderId,
            String idempotencyKey
    );

    void cancel(
            String pgTransactionId,
            long amount,
            String idempotencyKey
    );
}