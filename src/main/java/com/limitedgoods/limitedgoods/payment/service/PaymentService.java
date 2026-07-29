package com.limitedgoods.limitedgoods.payment.service;

import com.limitedgoods.limitedgoods.payment.dto.*;

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

    RefundResult cancel(
            String pgTransactionId,
            long amount,
            String idempotencyKey
    );

    RefundLookupResult lookupRefund(
            String pgTransactionId,
            String idempotencyKey
    );
}