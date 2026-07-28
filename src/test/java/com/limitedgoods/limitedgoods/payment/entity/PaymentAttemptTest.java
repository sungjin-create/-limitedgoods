package com.limitedgoods.limitedgoods.payment.entity;

import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import com.limitedgoods.limitedgoods.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptTest {

    @Test
    void createCopiesOrderAmountAndStartsProcessing() {
        Order order = order(32_000L);

        PaymentAttempt attempt = PaymentAttempt.create(order, "payment-key-1", "fingerprint");

        assertThat(attempt.getOrder()).isSameAs(order);
        assertThat(attempt.getAmount()).isEqualTo(32_000L);
        assertThat(attempt.getIdempotencyKey()).isEqualTo("payment-key-1");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
        assertThat(attempt.getRequestedAt()).isNotNull();
    }

    @Test
    void approveStoresGatewayResult() {
        PaymentAttempt attempt = PaymentAttempt.create(order(10_000L), "payment-key-2", "fingerprint");
        LocalDateTime approvedAt = LocalDateTime.now();

        attempt.approve(new PaymentResult("pg-transaction", 10_000L, approvedAt));

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.APPROVED);
        assertThat(attempt.getPgTransactionId()).isEqualTo("pg-transaction");
        assertThat(attempt.getApprovedAt()).isEqualTo(approvedAt);
    }

    @Test
    void declineStoresFailureDetails() {
        PaymentAttempt attempt = PaymentAttempt.create(order(10_000L), "payment-key-3", "fingerprint");

        attempt.decline("CARD_DECLINED", "카드사 거절");

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.DECLINED);
        assertThat(attempt.getFailureCode()).isEqualTo("CARD_DECLINED");
        assertThat(attempt.getFailureReason()).isEqualTo("카드사 거절");
    }

    @Test
    void unknownStoresReasonForReconciliation() {
        PaymentAttempt attempt = PaymentAttempt.create(order(10_000L), "payment-key-4", "fingerprint");

        attempt.markUnknown("gateway timeout");

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        assertThat(attempt.getFailureReason()).isEqualTo("gateway timeout");
    }

    private Order order(long amount) {
        return Order.create(
                new User(), amount, LocalDateTime.now().plusMinutes(5),
                "checkout", "order-fingerprint"
        );
    }
}
