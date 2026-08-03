package com.limitedgoods.limitedgoods.payment.entity;

import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.payment.dto.RefundResult;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "refund_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_refund_attempt_order_key",
                        columnNames = {"order_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uq_refund_attempt_pg_refund",
                        columnNames = {"pg_refund_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id", nullable = false)
    private PaymentAttempt paymentAttempt;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundAttemptStatus status;

    @Column(name = "pg_transaction_id", nullable = false)
    private String pgTransactionId;

    @Column(name = "pg_refund_id")
    private String pgRefundId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static RefundAttempt create(
            Order order,
            PaymentAttempt paymentAttempt,
            String idempotencyKey,
            long amount
    ) {
        LocalDateTime now = LocalDateTime.now();

        RefundAttempt attempt = new RefundAttempt();
        attempt.order = order;
        attempt.paymentAttempt = paymentAttempt;
        attempt.idempotencyKey = idempotencyKey;
        attempt.amount = amount;
        attempt.status = RefundAttemptStatus.PROCESSING;
        attempt.pgTransactionId = paymentAttempt.getPgTransactionId();
        attempt.requestedAt = now;
        attempt.updatedAt = now;

        return attempt;
    }

    public void approve(RefundResult result) {
        if (result.refundedAmount() != amount) {
            status = RefundAttemptStatus.UNKNOWN;
            failureCode = "REFUND_AMOUNT_MISMATCH";
            failureReason = "요청 환불액과 실제 환불액이 일치하지 않습니다.";
            updatedAt = LocalDateTime.now();
            return;
        }

        status = RefundAttemptStatus.APPROVED;
        pgRefundId = result.refundId();
        refundedAt = result.refundedAt();
        failureCode = null;
        failureReason = null;
        updatedAt = LocalDateTime.now();
    }

    public void decline(String code, String reason) {
        status = RefundAttemptStatus.DECLINED;
        failureCode = code;
        failureReason = reason;
        updatedAt = LocalDateTime.now();
    }

    public void markUnknown(String reason) {
        status = RefundAttemptStatus.UNKNOWN;
        failureReason = reason;
        updatedAt = LocalDateTime.now();
    }
}
